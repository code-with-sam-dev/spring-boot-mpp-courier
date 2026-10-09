#!/bin/sh
# The break chapter, part 1: the unsafe client pays whatever it is asked.
. "$(dirname "$0")/lib.sh"
{
echo "\$ bin/evidence/04-unsafe-overpay.sh"
echo "(Tempo Moderato testnet, chain 42431, test tokens only)"
echo "# courier-api started with --courier.premium.price=5.00"
echo "# retailer-assistant started with --spring.profiles.active=break"
start_courier --courier.premium.price=5.00
start_assistant --spring.profiles.active=break
run "bin/balance.sh"
run "curl -s '$A/break/quotes/premium?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
run "bin/balance.sh"
echo "# the same request through the safe client"
run "curl -s '$A/assistant/quotes/premium?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
run "bin/balance.sh"
run "curl -s '$A/assistant/payments' | python3 -m json.tool"
stop_assistant; stop_courier
} > evidence/04-unsafe-overpay.txt 2>&1
