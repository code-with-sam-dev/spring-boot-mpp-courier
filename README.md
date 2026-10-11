# Spring Boot, MCP and MPP: a courier quote that costs five cents

A small online retailer has an AI assistant that compares delivery quotes. The
standard quote is free. One premium courier charges 0.05 per quotation, and
wants to be paid before it answers. The assistant may pay, but it must not have
unlimited payment authority.

This repository is that story as real, runnable Java:

- `courier-api` is the courier. Its premium endpoint answers HTTP 402 with a
  payment challenge, using the Machine Payments Protocol (MPP) through
  `com.stripe:mpp-java` 0.1.6.
- `retailer-assistant` is the retailer's side: a hand-written MPP client that
  pays on Tempo testnet, a payment policy, an audit trail, and an MCP server that
  exposes all of it to an AI application as tools.
- `tempo-wallet` creates and funds the test wallets.

**Testnet only. Zero real money.** Every payment here is in pathUSD, a test
stablecoin on Tempo Moderato testnet (chain 42431), handed out free by the
testnet faucet. Every transaction in `evidence/` can be looked up on
https://explore.testnet.tempo.xyz.

## MCP and MPP are different things

| Aspect | MCP | MPP |
|---|---|---|
| Full name | Model Context Protocol | Machine Payments Protocol |
| Primary purpose | Connecting AI applications to tools and context | Paying for access to a resource over HTTP |
| Main interaction | The host calls a tool (`tools/call`, JSON-RPC 2.0) | 402 challenge, credential, receipt |
| Requires an LLM? | Not in the wire protocol | No |
| Handles payment? | Not in its core specification | Yes |
| Can they work together? | Yes | Yes |

- MCP answers "how does my AI application call a tool?". In this repo Claude Code
  (or any MCP host) calls `premium_delivery_quote` on `retailer-assistant`.
- MPP answers "how does a client pay for a paid HTTP resource?". In this repo the
  tool's HTTP client meets a 402 from `courier-api`, pays, and retries.
- MCP is not a payment protocol. MPP does not need an LLM: `MppPaymentClient`
  pays with no model anywhere, and so do the tests.
- MPP is payment-method agnostic. Tempo testnet is simply the settlement method
  chosen for this demo; the protocol also defines other methods.
- MPP removes the need for a pre-existing billing relationship with the
  provider (no account with the courier, no API key from it). It does not remove
  the need to fund a wallet, or to decide who is allowed to spend from it.

**MPP over MCP is not used here, on purpose.** MPP has a separate draft MCP
transport (`draft-payment-transport-mcp-00`) that carries challenges and
credentials inside MCP messages. This project keeps MPP on plain HTTP inside the
MCP tool instead, because: no Java implementation of that transport exists at
the time of writing; it would require the MCP host itself to understand
payments, while here any ordinary MCP host works; and keeping the two apart
shows exactly which protocol does which job. MCP carries the tool call; MPP
carries the payment.

## Three different failures, and what stops each

1. **Duplicate settlement**: two transfers on chain for one logical request.
   This is the bug `UnsafePaymentClient` shows: the response is lost, it retries
   from the top, gets a fresh challenge and pays again.
   *Stopped by* `MppPaymentClient` and `PaymentOperationStore`: the transfer is
   signed and written down (with its hash, which is known before broadcasting)
   before any money moves. After that, the client only ever resumes: the same
   signed transfer, the same credential, the same `Idempotency-Key`. It never
   signs a second transfer for that request.
2. **Duplicate redemption**: one transfer used to obtain the resource twice.
   *Stopped by* mpp-java's replay store, which rejects a reused transaction hash,
   and by `RedemptionService` on the courier, which rejects a redeemed hash
   presented for any other request (other parcel, other `Idempotency-Key`, other
   challenge, or no key at all), with 402 `verification-failed`.
3. **Recovery**: getting the already-paid result after the response was lost.
   The courier records each verified redemption (`RedemptionStore`: tx hash,
   challenge id, `Idempotency-Key`, request fingerprint, quote, receipt). When the
   SAME credential comes back with the SAME `Idempotency-Key` for the SAME
   request, it returns the stored quote with the original `Payment-Receipt`.
   This check runs before mpp-java, because the SDK would otherwise reject the
   reuse; the SDK's own replay protection stays in place behind it.

   A note on the spec: the core draft says servers "MUST NOT accept the same
   credential twice", and suggests `Idempotency-Key` so that a retried paid
   request returns "the same response as the original successful request without
   re-executing the operation". The courier never settles a credential twice; it
   only replays the stored response to the identical request. That is our reading
   of how the two rules fit together, not text from the spec.

If the courier will not hand the result over at all, the safe client stops,
marks the operation `UNDELIVERED`, and reports the transaction hash. It does not
pay again.

## Modules

### `courier-api` (port 8090), package `com.codewithsam.courier.provider`

| Class | Job |
|---|---|
| `quote.StandardQuoteController` | `GET /quotes/standard`, free |
| `quote.PremiumQuoteController` | `GET /quotes/premium`, paid per request |
| `quote.RateCard`, `QuoteRequest`, `QuoteResponse` | delivery prices in GBP (the delivery price is not the quote fee) |
| `payment.MppConfig` | one shared `TempoMethod` and one `MppHandler` bean |
| `payment.PremiumChargeProperties` | price, recipient address, realm, secret |
| `payment.PaymentGate` | verified receipt, or a `PaymentException` |
| `payment.PaymentProblemAdvice` | every payment failure becomes 402 + fresh challenge + `application/problem+json` + `Cache-Control: no-store` |
| `redemption.RedemptionService`, `RedemptionStore`, `Redemption` | what each payment bought; recovery and redemption checks |
| `misbehaviour.LostResponseSimulator` | break chapter only: drops the first N paid responses after payment |

`Cache-Control: no-store` on every 402 and `Cache-Control: private` on every
response carrying `Payment-Receipt` are added by this code: mpp-java does not
add them. Neither does it turn its `PaymentException` into an HTTP response; that
is `PaymentProblemAdvice`.

### `retailer-assistant` (port 8080), package `com.codewithsam.courier`

| Class | Job |
|---|---|
| `payment.MppPaymentClient` | GET, 402, policy, pay, retry with a `type=hash` credential, check the receipt; resume, never repay |
| `payment.PaymentPolicyService` | method `tempo`, intent `charge`, chain 42431, currency pathUSD, recipient on the allow-list, challenge not expired, at most 0.10 per request, at most 0.25 per day |
| `payment.PaymentAuditService` | every decision and step, with explorer links (never the credential) |
| `payment.PaymentOperationStore` | `PENDING_PAYMENT` to `BROADCAST` to `REDEEMED` (or `UNDELIVERED`), per logical request |
| `payment.TempoChargePayer` | attribution memo, `transferWithMemo`, credential |
| `payment.ChargeOffer` | what a challenge asks for, read before paying |
| `payment.UnsafePaymentClient` | break chapter only (profile `break`): no policy, no record, retries from the top |
| `quotation.QuotationService`, `QuotationController` | standard, premium, compare; REST under `/assistant` |
| `agent.CourierAssistant` | the MCP tools: `standard_delivery_quotes`, `premium_delivery_quote`, `payment_audit` |
| `config.PaymentConfig`, `PaymentPolicyProperties`, `CourierProperties` | wiring and limits |

The MCP server is Spring AI 2.0.1 (`spring-ai-starter-mcp-server-webmvc`,
streamable HTTP at `/mcp`). `.mcp.json` points Claude Code at it.

### `tempo-wallet`, package `com.codewithsam.courier.wallet`

`TempoWallet` signs an ordinary EIP-1559 transaction calling `transferWithMemo`
on pathUSD, using stock web3j (Tempo accepts standard Ethereum transactions);
`TempoRpc` is JSON-RPC over `java.net.http`; `WalletCli` creates the keys and
funds the payer from the faucet.

## Run it

Needs JDK 25 (`bin/java25.sh` finds it; set `JAVA_HOME_25` if it does not) and
network access to `https://rpc.moderato.tempo.xyz`.

```sh
bin/build.sh                # build everything, run the offline tests
bin/new-wallet.sh           # keys into .env (mode 600, gitignored), fund the payer
bin/balance.sh              # pathUSD balances of payer and courier
bin/courier.sh              # courier-api on 8090
bin/assistant.sh            # retailer-assistant on 8080, MCP at /mcp
curl 'localhost:8080/assistant/quotes/compare?from=London&to=Manchester&weightKg=2'
curl localhost:8080/assistant/payments
bin/challenge.sh            # ask for the premium quote unpaid, decode the 402 challenge
bin/audit.sh                # the payment audit, one row per line
```

`.env` holds `PAYER_PRIVATE_KEY`, `PAYER_ADDRESS`, `COURIER_RECIPIENT_ADDRESS` and
`MPP_SECRET_KEY`. The courier needs only its address, never a key. Nothing prints
a private key.

Ports: if 8080 or 8090 are taken, set `COURIER_PORT` and `ASSISTANT_PORT`. The
evidence scripts use 18090 and 18080 for that reason.

Break chapter switches:

```sh
bin/courier.sh --courier.premium.price=5.00                  # the courier raises its price
bin/courier.sh --courier.misbehaviour.lose-paid-responses=1  # the first paid answer is lost
bin/assistant.sh --spring.profiles.active=break              # adds GET /break/quotes/premium
```

## Tests

```sh
bin/tests.sh            # 44 offline tests, no network, one line each
bin/tests.sh testnet    # plus 6 integration tests that pay on Tempo testnet
./mvnw verify -Ptestnet # the same, plain Maven
```

The offline tests use a fake wallet and `MockRestServiceServer`: every policy
refusal, the memo binding, challenge parsing, recovery after a lost response or a
timeout, resuming after a crash before broadcast, the courier's redemption rules,
the unsafe client's two failures, and a real MCP client listing the tools.

The testnet tests start the real `courier-api` jar as a separate process and
check balances on chain: a paid quote moves exactly 0.05 to the courier; a policy
refusal leaves the payer's nonce and balance unchanged; the unsafe client pays
5.00 when the price is raised; a lost response costs the unsafe client 0.10 and
the safe client 0.05; a redeemed hash cannot buy a different request.

## Evidence

Each file starts with the command that produced it. Rerun any of them:

| File | Command |
|---|---|
| `evidence/00-wallets.txt` | `bin/new-wallet.sh` and `bin/balance.sh` |
| `evidence/01-http-exchange.txt` | `./mvnw -B -pl retailer-assistant verify -Ptestnet -Dit.test=HttpExchangeTranscriptTestnetIT` |
| `evidence/06-replay-rejected.txt` | written by the same test |
| `evidence/02-assistant-paid-quote.txt` | `bin/evidence/02-assistant-paid-quote.sh` |
| `evidence/03-policy-refusals.txt` | `bin/evidence/03-policy-refusals.sh` |
| `evidence/04-unsafe-overpay.txt` | `bin/evidence/04-unsafe-overpay.sh` |
| `evidence/05-lost-response.txt` | `bin/evidence/05-lost-response.sh` |
| `evidence/08-tests.txt` | `bin/tests.sh testnet` |
| `evidence/agent-claude-code.txt` | `bin/evidence/agent-claude-code.sh` |

The payment audit appears in 02 to 05 (`GET /assistant/payments`).

`agent-claude-code.txt` is a real headless Claude Code session using only this
project's MCP server: `claude -p --setting-sources project --strict-mcp-config
--mcp-config .mcp.json --tools "" --allowedTools <the three tools>`.

## Things to know

- **TLS.** The MPP spec says servers MUST NOT issue payment challenges over
  unencrypted HTTP, and clients MUST NOT send credentials over it. This demo runs
  on plain HTTP on localhost. That is a local development deviation; anything
  beyond your own machine needs HTTPS.
- **Standards status.** MPP's core specification is an Internet-Draft, not an
  IETF standard. At the time of writing (October 2026) the IETF datatracker lists
  the individual draft as expired; the living text is at https://paymentauth.org.
- **mpp-java is experimental.** Its README says the API is subject to change.
  This project pins 0.1.6, the latest release at the time of writing.
- **The attribution memo is mandatory in practice.** mpp-java (and the other MPP
  SDKs) reject a Tempo payment unless it is a `transferWithMemo` whose memo binds
  it to the server's realm and the challenge id. The Tempo charge draft does not
  say so. `TempoChargePayer.memoFor` builds it with mpp-java's `Attribution`.
- **Gas is estimated, never hard-coded.** A brand new account's first transfer
  cost about 545,000 gas on Tempo, because account creation is charged.
- **Fees.** Tempo has no native coin; the payer pays its own transaction fees in
  pathUSD, about 0.00004 per transfer in our runs. The policy limits count the
  price asked by the courier, not the fee.
- **Jackson.** mpp-java uses Jackson 2 (`com.fasterxml`); Spring Boot 4 and web3j
  6 use Jackson 3 (`tools.jackson`). Different packages, so they coexist. Spring
  Boot's dependency management resolves mpp-java's Jackson 2 to 2.21.5.
- **Push mode only.** The client broadcasts its own transfer and sends the hash
  (`type=hash`). Pull mode (the server broadcasts a signed Tempo 0x76
  transaction) is not implemented here.
- **Replay store.** mpp-java's default store is in memory, per process. The
  courier's redemption table (H2 file under `runs/`) survives a restart and also
  rejects a redeemed hash after one.
- **Credentials are bearer tokens.** The audit stores transaction hashes, not
  credentials. `evidence/01-http-exchange.txt` prints one spent, single-use
  testnet credential on purpose, to show what is on the wire.

## Versions (October 2026)

Java 25, Spring Boot 4.1.1, Spring AI 2.0.1 (MCP Java SDK underneath), mpp-java
0.1.6, web3j 6.0.0, H2. Tempo Moderato testnet: chain 42431, RPC
`https://rpc.moderato.tempo.xyz`, pathUSD at
`0x20c0000000000000000000000000000000000000` with 6 decimals.
