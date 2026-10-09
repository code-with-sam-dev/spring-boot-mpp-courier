#!/bin/sh
# A real Claude Code session using the retailer-assistant over MCP.
# Isolated: project settings only, no user plugins, no built-in tools, only the
# three MCP tools from .mcp.json. The raw stream is kept in runs/agent.jsonl.
. "$(dirname "$0")/lib.sh"
PROMPT="We run a small online shop and need to send a 2 kg parcel from London to \
Manchester. Use the courier tools to compare the delivery options and recommend one. \
You may buy the premium quote if the payment policy allows it. Finish by showing \
what was paid, from the payment audit."
TOOLS="mcp__courier-assistant__standard_delivery_quotes \
mcp__courier-assistant__premium_delivery_quote mcp__courier-assistant__payment_audit"
start_courier; start_assistant
claude -p "$PROMPT" --model "${MODEL:-sonnet}" \
  --setting-sources project --strict-mcp-config --mcp-config .mcp.json \
  --tools "" --allowedTools $TOOLS \
  --disable-slash-commands --no-chrome \
  --output-format stream-json --verbose </dev/null > runs/agent.jsonl
stop_assistant; stop_courier
{
echo "\$ bin/evidence/agent-claude-code.sh"
echo "(claude -p --setting-sources project --strict-mcp-config --mcp-config .mcp.json"
echo " --tools \"\" --allowedTools <the three courier-assistant tools>)"
echo "Tempo Moderato testnet, chain 42431, test tokens only."
python3 bin/evidence/agent-transcript.py "$PROMPT" < runs/agent.jsonl
} > evidence/agent-claude-code.txt
