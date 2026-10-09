#!/bin/sh
# Build every module and run the offline tests.
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
. bin/java25.sh
./mvnw -q -B install
