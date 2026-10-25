"""OpenDroid memory service: a thin REST wrapper around the open-source `mem0ai` library.

Runs inside the sandbox on 127.0.0.1:8765. Configuration (LLM + embedder providers and keys)
is written by the Android app to /root/.opendroid/mem0.json and re-read on POST /reload.
"""
import json
import os
import threading

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

CONFIG_PATH = "/root/.opendroid/mem0.json"
USER_ID = "opendroid"

app = FastAPI(title="opendroid-memory")
_lock = threading.Lock()
_memory = None
_error = "not configured"


def _load():
    global _memory, _error
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
                        from mem0 import Memory  # type: ignore[import-not-found]  # installed in sandbox venv (/opt/mem0)

            _memory = Memory.from_config(cfg)
            _error = ""
        except Exception as e:  # surfaced to the UI via /health
            _memory, _error = None, f"{type(e).__name__}: {e}"


def _mem():
    if _memory is None:
        raise HTTPException(status_code=503, detail=_error or "not configured")
    return _memory


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
    return {"ok": _memory is not None, "error": _error}


@app.post("/reload")
def reload():
    _load()
    return health()


@app.post("/add")
def add(req: AddReq):
    return _mem().add(req.text, user_id=USER_ID, metadata=req.metadata)


@app.post("/search")
def search(req: SearchReq):
    return {"results": _results(_mem().search(req.query, user_id=USER_ID, limit=req.limit))}


@app.get("/all")
def all_memories():
    return {"results": _results(_mem().get_all(user_id=USER_ID))}


@app.put("/memory/{memory_id}")
def update(memory_id: str, req: UpdateReq):
    return _mem().update(memory_id, req.text)


@app.delete("/memory/{memory_id}")
def delete(memory_id: str):
    _mem().delete(memory_id)
    return {"ok": True}


@app.delete("/all")
def delete_all():
    _mem().delete_all(user_id=USER_ID)
    return {"ok": True}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8765, log_level="warning")
