#!/bin/sh
# Run the tests and print one line per test, from the Surefire/Failsafe reports.
#   bin/tests.sh            offline tests (no network)
#   bin/tests.sh testnet    also the integration tests on Tempo testnet
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
. bin/java25.sh
if [ "$1" = "testnet" ]; then
  ./mvnw -q -B install -DskipTests >/dev/null 2>&1
  ./mvnw -q -B verify -Ptestnet >/dev/null 2>&1
  REPORTS="*/target/surefire-reports/TEST-*.xml */target/failsafe-reports/TEST-*.xml"
else
  ./mvnw -q -B install >/dev/null 2>&1
  REPORTS="*/target/surefire-reports/TEST-*.xml"
fi
python3 - $REPORTS <<'PY'
import re, sys, xml.etree.ElementTree as ET
total = failed = 0
for path in sys.argv[1:]:
    suite = ET.parse(path).getroot()
    print(path.split('/')[0] + "  " + suite.get('name').split('.')[-1])
    for case in suite.iter('testcase'):
        bad = case.find('failure') is not None or case.find('error') is not None
        total += 1; failed += bad
        name = re.sub(r'(?<!^)(?=[A-Z])', ' ', case.get('name')).lower()
        print(f"  {'FAIL' if bad else 'ok  '} {name}")
print(f"\n{total} tests, {failed} failed")
PY
