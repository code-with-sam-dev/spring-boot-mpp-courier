#!/bin/sh
# pathUSD balance of the payer and the courier (from .env), or of the addresses given.
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
. bin/java25.sh
JAR=$(ls tempo-wallet/target/tempo-wallet-*-exec.jar)
if [ $# -eq 0 ]; then
  set -a; . "${ENV_FILE:-./.env}"; set +a
  set -- "$PAYER_ADDRESS" "$COURIER_RECIPIENT_ADDRESS"
fi
"$JAVA_HOME/bin/java" -jar "$JAR" balance "$@"
