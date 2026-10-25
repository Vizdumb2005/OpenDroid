"""OpenDroid memory service: a REST service supporting both the full `mem0ai` vector library
and an embedded local SQLite engine when Mem0 is offline or unconfigured.

Runs inside the sandbox on 127.0.0.1:8765. Configuration (LLM + embedder providers and keys)
is written by the Android app to /root/.opendroid/mem0.json and re-read on POST /reload.
"""
import json
import os
import sqlite3
import threading
import time
import uuid

from fastapi import FastAPI
from pydantic import BaseModel

CONFIG_PATH = "/root/.opendroid/mem0.json"
SQLITE_DB_PATH = "/root/.opendroid/memories.db"
USER_ID = "opendroid"

app = FastAPI(title="opendroid-memory")
_lock = threading.Lock()
_memory = None
_error = "not configured"


def _init_sqlite():
    os.makedirs(os.path.dirname(SQLITE_DB_PATH), exist_ok=True)
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS memories (
                id TEXT PRIMARY KEY,
                memory TEXT NOT NULL,
                category TEXT DEFAULT 'fact',
                created_at REAL
            )
        """)


def _load():
    global _memory, _error
    _init_sqlite()
    with _lock:
        _memory, _error = None, "not configured"
        if not os.path.exists(CONFIG_PATH):
            return
        try:
            with open(CONFIG_PATH) as f:
                cfg = json.load(f)
            os.makedirs("/root/.opendroid/qdrant", exist_ok=True)
            cfg.setdefault("vector_store", {
                "provider": "qdrant",
                "config": {
                    "path": "/root/.opendroid/qdrant",
                    "collection_name": "opendroid",
                    "embedding_model_dims": cfg.pop("embedding_dims", 1536),
                    "on_disk": True,
                },
            })
            cfg.setdefault("history_db_path", "/root/.opendroid/history.db")
            from mem0 import Memory  # type: ignore[import-not-found]

            _memory = Memory.from_config(cfg)
            _error = ""
        except Exception as e:
            _memory, _error = None, f"{type(e).__name__}: {e}"


def _results(x):
    return x.get("results", []) if isinstance(x, dict) else x


class AddReq(BaseModel):
    text: str
    metadata: dict | None = None


class SearchReq(BaseModel):
    query: str
    limit: int = 5


class UpdateReq(BaseModel):
    text: str


@app.on_event("startup")
def _startup():
    _load()


@app.get("/health")
def health():
    return {
        "ok": True,
        "engine": "mem0" if _memory is not None else "sqlite_fallback",
        "configured": _memory is not None,
        "error": _error if _memory is None else "",
    }


@app.post("/reload")
def reload():
    _load()
    return health()


@app.post("/add")
def add(req: AddReq):
    if _memory is not None:
        try:
            return _mem().add(req.text, user_id=USER_ID, metadata=req.metadata)
        except Exception:
            pass
    # Local SQLite storage fallback
    mem_id = str(uuid.uuid4())
    cat = (req.metadata or {}).get("category", "fact") if req.metadata else "fact"
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        conn.execute(
            "INSERT INTO memories (id, memory, category, created_at) VALUES (?, ?, ?, ?)",
            (mem_id, req.text, cat, time.time()),
        )
    return {"results": [{"id": mem_id, "memory": req.text, "metadata": {"category": cat}}]}


@app.post("/search")
def search(req: SearchReq):
    if _memory is not None:
        try:
            return {"results": _results(_memory.search(req.query, user_id=USER_ID, limit=req.limit))}
        except Exception:
            pass
    tokens = [t.strip().lower() for t in req.query.split() if len(t.strip()) > 1]
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        if tokens:
            where = " OR ".join(["LOWER(memory) LIKE ?"] * len(tokens))
            params = [f"%{t}%" for t in tokens]
            rows = conn.execute(f"SELECT id, memory, category FROM memories WHERE {where} LIMIT {req.limit}", params).fetchall()
        else:
            rows = conn.execute(f"SELECT id, memory, category FROM memories ORDER BY created_at DESC LIMIT {req.limit}").fetchall()
    return {
        "results": [
            {"id": r[0], "memory": r[1], "metadata": {"category": r[2]}}
            for r in rows
        ]
    }


@app.get("/all")
def all_memories():
    if _memory is not None:
        try:
            return {"results": _results(_memory.get_all(user_id=USER_ID))}
        except Exception:
            pass
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        rows = conn.execute("SELECT id, memory, category FROM memories ORDER BY created_at DESC").fetchall()
    return {
        "results": [
            {"id": r[0], "memory": r[1], "metadata": {"category": r[2]}}
            for r in rows
        ]
    }


@app.put("/memory/{memory_id}")
def update(memory_id: str, req: UpdateReq):
    if _memory is not None:
        try:
            return _memory.update(memory_id, req.text)
        except Exception:
            pass
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        conn.execute("UPDATE memories SET memory = ? WHERE id = ?", (req.text, memory_id))
    return {"id": memory_id, "memory": req.text}


@app.delete("/memory/{memory_id}")
def delete(memory_id: str):
    if _memory is not None:
        try:
            _memory.delete(memory_id)
            return {"ok": True}
        except Exception:
            pass
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        conn.execute("DELETE FROM memories WHERE id = ?", (memory_id,))
    return {"ok": True}


@app.delete("/all")
def delete_all():
    if _memory is not None:
        try:
            _memory.delete_all(user_id=USER_ID)
            return {"ok": True}
        except Exception:
            pass
    with sqlite3.connect(SQLITE_DB_PATH) as conn:
        conn.execute("DELETE FROM memories")
    return {"ok": True}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8765, log_level="warning")
