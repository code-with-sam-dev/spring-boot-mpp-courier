"""Turns a Claude Code stream-json log into a readable transcript of tool calls."""
import json
import sys

prompt = sys.argv[1]
events = [json.loads(line) for line in sys.stdin if line.strip()]
init = next(e for e in events if e.get("type") == "system" and e.get("subtype") == "init")
print(f"Claude Code {init.get('claude_code_version')}, model {init.get('model')}")
print("MCP servers:", ", ".join(f"{s['name']} ({s['status']})" for s in init["mcp_servers"]))
print("Tools available:", ", ".join(init["tools"]))
print("Plugins:", ", ".join(p["name"] for p in init.get("plugins", [])) or "none",
      "| Skills:", ", ".join(init.get("skills", [])) or "none")
print("\nUSER:", prompt, "\n")
for e in events:
    for block in (e.get("message") or {}).get("content", []) if isinstance(
            (e.get("message") or {}).get("content"), list) else []:
        if e["type"] == "assistant" and block.get("type") == "tool_use":
            print(f"TOOL CALL  {block['name'].split('__')[-1]} {json.dumps(block['input'])}")
        elif e["type"] == "user" and block.get("type") == "tool_result":
            content = block.get("content")
            text = content if isinstance(content, str) else " ".join(
                c.get("text", "") for c in content)
            print(f"TOOL RESULT {text}\n")
        elif e["type"] == "assistant" and block.get("type") == "text" and block["text"].strip():
            print("ASSISTANT:", block["text"].strip(), "\n")
result = next(e for e in events if e.get("type") == "result")
print(f"({result.get('num_turns')} turns, {result.get('duration_ms', 0) / 1000:.0f} s)")
