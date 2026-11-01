"""Minimal client for the Blockbench MCP plugin (streamable HTTP, JSON-RPC).

Usage:
  python tools/bb.py tools                      # list tool names + descriptions
  python tools/bb.py schema <tool>              # print one tool's input schema
  python tools/bb.py call <tool> '<json args>'  # call a tool
  python tools/bb.py call <tool> @args.json     # args from file
"""
import json
import sys
import urllib.request

URL = "http://localhost:3000/bb-mcp"
_session = None
_next_id = 0


def _post(payload):
    global _session
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if _session:
        headers["mcp-session-id"] = _session
    req = urllib.request.Request(URL, data=json.dumps(payload).encode(), headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=120) as resp:
        sid = resp.headers.get("mcp-session-id")
        if sid:
            _session = sid
        body = resp.read().decode("utf-8", "replace")
        ctype = resp.headers.get("content-type", "")
    if not body.strip():
        return None
    if "text/event-stream" in ctype:
        result = None
        for line in body.splitlines():
            if line.startswith("data:"):
                msg = json.loads(line[5:].strip())
                if "result" in msg or "error" in msg:
                    result = msg
        return result
    return json.loads(body)


def _rpc(method, params=None):
    global _next_id
    _next_id += 1
    msg = _post({"jsonrpc": "2.0", "id": _next_id, "method": method, "params": params or {}})
    if msg and "error" in msg:
        raise RuntimeError(json.dumps(msg["error"]))
    return msg["result"] if msg else None


def connect():
    _rpc("initialize", {
        "protocolVersion": "2025-06-18",
        "capabilities": {},
        "clientInfo": {"name": "gamblecraft-tools", "version": "1"},
    })
    _post({"jsonrpc": "2.0", "method": "notifications/initialized"})


def list_tools():
    tools, cursor = [], None
    while True:
        res = _rpc("tools/list", {"cursor": cursor} if cursor else {})
        tools += res["tools"]
        cursor = res.get("nextCursor")
        if not cursor:
            return tools


def call(name, args, image_path=None):
    """Calls a tool and returns its text; an image it returns is written to image_path when one is given."""
    res = _rpc("tools/call", {"name": name, "arguments": args})
    out = []
    for part in res.get("content", []):
        if part.get("type") == "text":
            out.append(part["text"])
        elif part.get("type") == "image":
            if image_path:
                import base64
                with open(image_path, "wb") as f:
                    f.write(base64.b64decode(part["data"]))
            out.append(f"<image {part.get('mimeType')} {len(part.get('data', ''))} b64 chars>")
        else:
            out.append(json.dumps(part))
    if res.get("isError"):
        raise RuntimeError("\n".join(out))
    return "\n".join(out)


def main(argv):
    connect()
    cmd = argv[1] if len(argv) > 1 else "tools"
    if cmd == "tools":
        for t in list_tools():
            desc = (t.get("description") or "").replace("\n", " ")
            print(f"{t['name']}: {desc[:160]}")
    elif cmd == "schema":
        for t in list_tools():
            if t["name"] == argv[2]:
                print(json.dumps(t.get("inputSchema"), indent=1))
    elif cmd == "call":
        raw = argv[3] if len(argv) > 3 else "{}"
        if raw.startswith("@"):
            with open(raw[1:], encoding="utf-8") as f:
                raw = f.read()
        print(call(argv[2], json.loads(raw)))
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
