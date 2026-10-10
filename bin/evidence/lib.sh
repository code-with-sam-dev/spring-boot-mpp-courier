# Shared by the evidence scripts. Starts the two apps on spare ports, so they do
# not collide with anything already on 8080/8090, and stops them afterwards.
DIR=$(cd "$(dirname "$0")/../.." && pwd); cd "$DIR"
export COURIER_PORT=${COURIER_PORT:-18090} ASSISTANT_PORT=${ASSISTANT_PORT:-18080}
C=http://localhost:$COURIER_PORT
A=http://localhost:$ASSISTANT_PORT
set -a; . "${ENV_FILE:-./.env}"; set +a
mkdir -p runs evidence

start_courier() {   # extra Spring properties as arguments
  stop_courier
  bin/courier.sh --spring.datasource.url=jdbc:h2:mem:courier "$@" \
    > runs/courier.log 2>&1 &
  wait_for "$C/quotes/standard?from=a&to=b&weightKg=1"
}

start_assistant() { # a fresh payment database each time, plus extra properties
  stop_assistant
  rm -f runs/evidence-assistant*.db
  bin/assistant.sh --spring.datasource.url=jdbc:h2:file:./runs/evidence-assistant "$@" \
    > runs/assistant.log 2>&1 &
  wait_for "$A/assistant/payments"
}

stop_courier() { pkill -f courier-api-1.0.0-exec.jar 2>/dev/null || true; sleep 1; }
stop_assistant() { pkill -f retailer-assistant-1.0.0-exec.jar 2>/dev/null || true; sleep 1; }

wait_for() {
  for _ in $(seq 60); do curl -sf "$1" >/dev/null 2>&1 && return 0; sleep 1; done
  echo "did not start: $1" >&2; exit 1
}

say() { printf '\n$ %s\n' "$*"; }
run() { say "$@"; sh -c "$*"; echo; }
json() { python3 -m json.tool; }
balances() { bin/balance.sh; }
