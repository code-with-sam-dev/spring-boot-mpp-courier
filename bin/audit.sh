#!/bin/sh
# The assistant's payment audit, one row per line: every approval, refusal,
# payment, lost response and delivery, oldest first.
PORT=${ASSISTANT_PORT:-8080}
curl -s "http://localhost:$PORT/assistant/payments" | python3 -c '
import json, sys
for r in json.load(sys.stdin):
    tx = (r["txHash"] or "-")[:12]
    print("%2s  %-6s  %-17s  %5s  tx %-12s  %s" % (r["id"], r["client"], r["outcome"], r["amount"], tx, r["detail"]))
'
