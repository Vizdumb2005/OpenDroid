"""stdio MCP server that exposes OpenDroid's Mem0 service to OpenCode as memory tools."""
import json
import urllib.request

from mcp.server.fastmcp import FastMCP  # type: ignore[import-not-found]  # installed in sandbox venv (/opt/mem0)

BASE = "http://127.0.0.1:8765"
mcp = FastMCP("memory")


def _call(method: str, path: str, body: dict | None = None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method, headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read().decode())
    except Exception as e:
        return {"error": str(e)}


@mcp.tool()
def memory_add(text: str) -> str:
    """Store a durable fact, preference or decision in long-term memory."""
    return json.dumps(_call("POST", "/add", {"text": text}))


@mcp.tool()
def memory_search(query: str, limit: int = 5) -> str:
    """Search long-term memory for facts relevant to the query."""
    return json.dumps(_call("POST", "/search", {"query": query, "limit": limit}))


@mcp.tool()
def memory_list() -> str:
    """List every stored long-term memory."""
    return json.dumps(_call("GET", "/all"))


if __name__ == "__main__":
    mcp.run()
