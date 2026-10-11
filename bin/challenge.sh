#!/bin/sh
# Ask for the premium quote with no payment and decode the challenge's request
# parameter: what the courier wants paid, in what token, to whom, on which chain.
#   bin/challenge.sh [from] [to] [weightKg]
set -e
PORT=${COURIER_PORT:-8090}
URL="http://localhost:$PORT/quotes/premium?from=${1:-London}&to=${2:-Manchester}&weightKg=${3:-2}"
curl -s -D - -o /dev/null "$URL" | python3 -c '
import base64, json, re, sys
header = next(l for l in sys.stdin if l.lower().startswith("www-authenticate:"))
fields = dict(re.findall(r"(\w+)=\"([^\"]*)\"", header))
print("method  =", fields["method"])
print("intent  =", fields["intent"])
print("expires =", fields.get("expires", "-"))
raw = fields["request"] + "=" * (-len(fields["request"]) % 4)
print("request =", json.dumps(json.loads(base64.urlsafe_b64decode(raw)), indent=4))
'
