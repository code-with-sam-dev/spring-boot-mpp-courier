#!/bin/sh
# Every way the policy says no. A refusal must never put anything on chain,
# so the payer's nonce (transaction count) is shown before and after.
. "$(dirname "$0")/lib.sh"
nonce() {
  curl -s https://rpc.moderato.tempo.xyz -H 'content-type: application/json' -d \
    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"eth_getTransactionCount\",\"params\":[\"$PAYER_ADDRESS\",\"pending\"]}" \
    | python3 -c 'import json,sys; print("payer nonce", int(json.load(sys.stdin)["result"],16))'
}
{
echo "\$ bin/evidence/03-policy-refusals.sh"
echo "(Tempo Moderato testnet, chain 42431, test tokens only)"
echo; echo "## 1. The courier raises the premium price to 5.00 pathUSD"
start_courier --courier.premium.price=5.00; start_assistant
nonce; balances
run "curl -s '$A/assistant/quotes/premium?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
nonce; balances

echo; echo "## 2. The challenge names a recipient that is not an approved courier"
start_courier --courier.premium.recipient=0x00000000000000000000000000000000000bad00
start_assistant
nonce
run "curl -s '$A/assistant/quotes/premium?from=London&to=Manchester&weightKg=2' | python3 -m json.tool"
nonce

echo; echo "## 3. The daily budget (0.25) runs out after five 0.05 quotes"
start_courier; start_assistant
for kg in 1 2 3 4 5 6; do
  run "curl -s '$A/assistant/quotes/premium?from=London&to=Manchester&weightKg=$kg' | python3 -c 'import json,sys; r=json.load(sys.stdin); print(r[\"status\"], r[\"quoteFee\"], r[\"paymentTx\"] or r[\"reason\"])'"
done
nonce
run "curl -s '$A/assistant/payments' | python3 -c 'import json,sys
for e in json.load(sys.stdin): print(e[\"outcome\"].ljust(9), str(e[\"amount\"]).ljust(5), e[\"txHash\"] or e[\"detail\"])'"
stop_assistant; stop_courier
} > evidence/03-policy-refusals.txt 2>&1
