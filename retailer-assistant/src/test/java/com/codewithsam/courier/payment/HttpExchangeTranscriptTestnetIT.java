package com.codewithsam.courier.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewithsam.courier.wallet.EnvFile;
import com.codewithsam.courier.wallet.SignedTransfer;
import com.codewithsam.courier.wallet.TempoNetwork;
import com.codewithsam.courier.wallet.TempoRpc;
import com.codewithsam.courier.wallet.TempoWallet;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Receipt;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * One MPP exchange, step by step, written to evidence/ as a transcript. Each step
 * uses the same classes MppPaymentClient uses. Testnet tokens only.
 */
@Tag("testnet")
class HttpExchangeTranscriptTestnetIT {

    private static final String COMMAND = "./mvnw -B -pl retailer-assistant verify"
            + " -Ptestnet -Dit.test=HttpExchangeTranscriptTestnetIT";
    private static final String QUOTE =
            "/quotes/premium?from=London&to=Manchester&weightKg=2";

    private final EnvFile env = new EnvFile(Path.of("../.env"));
    private final StringBuilder exchange = new StringBuilder();
    private final StringBuilder replay = new StringBuilder();

    @Test
    void recordsOneRealExchangeAndTheReplayAttempts() throws Exception {
        TempoNetwork network = TempoNetwork.MODERATO;
        TempoChargePayer payer = new TempoChargePayer(new TempoWallet(
                new TempoRpc(network.rpcUrl()), network.chainId(),
                env.get("PAYER_PRIVATE_KEY").orElseThrow()), network);
        CourierHttp http = new CourierHttp(RestClient.create());

        Map<String, String> courierEnv = Map.of(
                "COURIER_RECIPIENT_ADDRESS",
                env.get("COURIER_RECIPIENT_ADDRESS").orElseThrow(),
                "MPP_SECRET_KEY", env.get("MPP_SECRET_KEY").orElseThrow());
        try (CourierProcess courier = CourierProcess.start(18196, courierEnv)) {
            URI uri = courier.uri(QUOTE);
            String key = "transcript-" + System.currentTimeMillis();

            step("1. Ask for the premium quote with no payment");
            exchange.append("> GET ").append(QUOTE).append("\n> Idempotency-Key: ")
                    .append(key).append("\n\n");
            HttpReply first = http.get(uri, null, key);
            response(exchange, first);
            Challenge challenge = TempoChargePayer.challengeFrom(first);
            exchange.append("decoded request = ").append(decode(challenge.requestB64()))
                    .append("\n\n");

            step("2. Read the challenge (what PaymentPolicyService checks)");
            exchange.append(ChargeOffer.from(challenge)).append("\n\n");

            step("3. Pay: transferWithMemo, memo bound to realm and challenge");
            exchange.append("memo = ").append(TempoChargePayer.memoFor(challenge))
                    .append("\n");
            SignedTransfer transfer = payer.sign(challenge);
            exchange.append("tx hash, known before broadcast = ")
                    .append(transfer.hash()).append("\n");
            payer.settle(transfer);
            exchange.append("mined: ").append(network.transactionUrl(transfer.hash()))
                    .append("\n\n");

            step("4. Retry with the credential (push mode, type=hash)");
            String credential = payer.authorization(challenge, transfer.hash());
            exchange.append("> GET ").append(QUOTE).append("\n> Idempotency-Key: ")
                    .append(key).append("\n> Authorization: ").append(credential)
                    .append("\n\ndecoded credential = ")
                    .append(decode(credential.substring("Payment ".length())))
                    .append("\n\n");
            HttpReply paid = http.get(uri, credential, key);
            response(exchange, paid);
            String receipt = paid.headers().getFirst("Payment-Receipt");
            exchange.append("decoded receipt = ").append(decode(receipt)).append("\n");
            assertThat(paid.status()).isEqualTo(200);
            assertThat(Receipt.fromPaymentReceipt(receipt).reference())
                    .isEqualTo(transfer.hash());

            step(replay, "A. Same credential, same Idempotency-Key, same parcel");
            replay.append("(recovering a lost response: stored result, no payment)\n");
            response(replay, http.get(uri, credential, key));
            step(replay, "B. Same credential, same Idempotency-Key, a 9 kg parcel");
            URI nineKilos = courier.uri(QUOTE.replace("weightKg=2", "weightKg=9"));
            HttpReply other = http.get(nineKilos, credential, key);
            response(replay, other);
            step(replay, "C. Same credential, no Idempotency-Key");
            HttpReply bare = http.get(uri, credential, null);
            response(replay, bare);
            assertThat(other.status()).isEqualTo(402);
            assertThat(bare.status()).isEqualTo(402);
        }
        write("01-http-exchange.txt", exchange);
        write("06-replay-rejected.txt", replay);
    }

    private void step(String title) {
        step(exchange, title);
    }

    private static void step(StringBuilder out, String title) {
        out.append("== ").append(title).append(" ==\n");
    }

    private static void response(StringBuilder out, HttpReply reply) {
        out.append("< HTTP/1.1 ").append(reply.status()).append("\n");
        reply.headers().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("Date")
                    && !name.equalsIgnoreCase("Transfer-Encoding")) {
                values.forEach(v -> out.append("< ").append(name).append(": ").append(v)
                        .append("\n"));
            }
        });
        out.append("\n").append(reply.body()).append("\n\n");
    }

    private static String decode(String base64url) {
        byte[] bytes = Base64.getUrlDecoder().decode(base64url);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void write(String name, StringBuilder body) throws Exception {
        Files.writeString(Path.of("../evidence", name), "$ " + COMMAND + "\n"
                + "(Tempo Moderato testnet, chain 42431, test tokens only)\n\n" + body);
    }
}
