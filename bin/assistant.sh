#!/bin/sh
# Start retailer-assistant on $ASSISTANT_PORT (default 8080): REST under
# /assistant, MCP at /mcp. It calls the courier on $COURIER_PORT (default 8090).
#   bin/assistant.sh --spring.profiles.active=break   adds the unsafe client
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
. bin/java25.sh
set -a; . ./.env; set +a
mkdir -p runs
exec "$JAVA_HOME/bin/java" -jar retailer-assistant/target/retailer-assistant-*-exec.jar \
  --server.port="${ASSISTANT_PORT:-8080}" \
  --assistant.courier.base-url="http://localhost:${COURIER_PORT:-8090}" "$@"
