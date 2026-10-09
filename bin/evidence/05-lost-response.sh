#!/bin/sh
# The break chapter, part 2: the courier takes the payment and the answer is lost.
# Unsafe client: retries from the top and pays a second time.
# Safe client: resumes from its record, presents the same credential, pays once.
. "$(dirname "$0")/lib.sh"
{
echo "\$ bin/evidence/05-lost-response.sh"
echo "(Tempo Moderato testnet, chain 42431, test tokens only)"
echo; echo "## Unsafe client. courier-api: --courier.misbehaviour.lose-paid-responses=1"
start_courier --courier.misbehaviour.lose-paid-responses=1
start_assistant --spring.profiles.active=break
run "bin/balance.sh"
run "curl -s '$A/break/quotes/premium?from=London&to=Manchester&weightKg=3' | python3 -m json.tool"
run "bin/balance.sh"
run "curl -s '$A/assistant/payments' | python3 -m json.tool"
echo "# courier-api log"
grep -E "PaymentGate|LostResponse|PaymentProblemAdvice" runs/courier.log | sed 's/^.*\] //'

echo; echo "## Safe client. A fresh courier-api with the same misbehaviour"
start_courier --courier.misbehaviour.lose-paid-responses=1
start_assistant
run "bin/balance.sh"
run "curl -s '$A/assistant/quotes/premium?from=London&to=Manchester&weightKg=3' | python3 -m json.tool"
run "bin/balance.sh"
run "curl -s '$A/assistant/payments' | python3 -m json.tool"
echo "# courier-api log"
grep -E "PaymentGate|LostResponse|Redemption|PaymentProblemAdvice" runs/courier.log | sed 's/^.*\] //'
stop_assistant; stop_courier
} > evidence/05-lost-response.txt 2>&1
