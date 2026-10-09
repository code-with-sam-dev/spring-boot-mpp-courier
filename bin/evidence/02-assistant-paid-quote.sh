#!/bin/sh
# The assistant compares a free standard quote with a paid premium one.
. "$(dirname "$0")/lib.sh"
{
echo "\$ bin/evidence/02-assistant-paid-quote.sh"
echo "(Tempo Moderato testnet, chain 42431, test tokens only; apps on $COURIER_PORT and $ASSISTANT_PORT)"
start_courier; start_assistant
run "bin/balance.sh"
run "curl -s '$A/assistant/quotes/standard?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
run "curl -s '$A/assistant/quotes/compare?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
echo "# asked again the same day: the record answers, nothing is paid"
run "curl -s '$A/assistant/quotes/premium?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
run "bin/balance.sh"
run "curl -s '$A/assistant/payments' | python3 -m json.tool"
echo "# courier-api log"
grep -E "PaymentGate|PaymentProblemAdvice|Redemption" runs/courier.log | sed 's/^.*\] //'
stop_assistant; stop_courier
} > evidence/02-assistant-paid-quote.txt 2>&1
