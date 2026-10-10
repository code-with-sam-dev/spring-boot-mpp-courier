#!/bin/sh
# Start courier-api on $COURIER_PORT (default 8090). Extra arguments are Spring
# properties, for the break chapter:
#   bin/courier.sh --courier.premium.price=5.00
#   bin/courier.sh --courier.misbehaviour.lose-paid-responses=1
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
. bin/java25.sh
set -a; . "${ENV_FILE:-./.env}"; set +a
PORT=${COURIER_PORT:-8090}
mkdir -p runs
exec "$JAVA_HOME/bin/java" -jar courier-api/target/courier-api-*-exec.jar \
  --server.port="$PORT" --courier.premium.realm="localhost:$PORT" "$@"
