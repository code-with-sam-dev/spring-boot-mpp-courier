#!/bin/sh
# Create the payer and courier wallets on Tempo testnet, keys into .env (mode 600).
# Prints addresses and balances only. Testnet tokens, no real money.
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
. bin/java25.sh
JAR=$(ls tempo-wallet/target/tempo-wallet-*-exec.jar 2>/dev/null || true)
if [ -z "$JAR" ]; then
  ./mvnw -q -B -pl tempo-wallet package -DskipTests
  JAR=$(ls tempo-wallet/target/tempo-wallet-*-exec.jar)
fi
"$JAVA_HOME/bin/java" -jar "$JAR" new-courier "${ENV_FILE:-.env}"
"$JAVA_HOME/bin/java" -jar "$JAR" new-payer "${ENV_FILE:-.env}"
