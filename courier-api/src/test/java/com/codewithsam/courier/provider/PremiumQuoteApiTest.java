package com.codewithsam.courier.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewithsam.courier.provider.quote.QuoteRequest;
import com.codewithsam.courier.provider.quote.QuoteResponse;
import com.codewithsam.courier.provider.redemption.Redemption;
import com.codewithsam.courier.provider.redemption.RedemptionStore;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Credential;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// No chain is touched here: every path below is decided before mpp-java polls Tempo.
@SpringBootTest(properties = {
        "courier.premium.recipient=0xe614e43e4fbf9867bdf4e2ddbd3f628658b93ddb",
        "courier.premium.secret-key=test-secret-not-real",
        "spring.datasource.url=jdbc:h2:mem:courier-test"})
@AutoConfigureMockMvc
class PremiumQuoteApiTest {

    private static final String QUERY = "?from=London&to=Manchester&weightKg=2";
    private static final String COURIER = "0xe614e43e4fbf9867bdf4e2ddbd3f628658b93ddb";
    private static final String PATH_USD = "0x20c0000000000000000000000000000000000000";
    // Each test pays with its own (pretend) transaction.
    private final String paidHash = "0x" + HexFormat.of().formatHex(
            ByteBuffer.allocate(32).putLong(System.nanoTime()).array());

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RedemptionStore redemptions;

    @Test
    void theStandardQuoteIsFree() {
        assertThat(mvc.get().uri("/quotes/standard" + QUERY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.courier")
                .isEqualTo("Parcel Post Standard");
    }

    @Test
    void thePremiumQuoteAsksForPaymentWithAChallenge() {
        MvcTestResult result = mvc.get().uri("/quotes/premium" + QUERY).exchange();

        assertThat(result).hasStatus(402)
                .hasHeader("Cache-Control", "no-store")
                .hasContentType("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://paymentauth.org/problems/payment-required");
        Challenge challenge = challengeFrom(result);
        assertThat(challenge.method()).isEqualTo("tempo");
        assertThat(challenge.intent()).isEqualTo("charge");
        assertThat(challenge.realm()).isEqualTo("localhost:8090");
        assertThat(challenge.request()).containsEntry("amount", "50000")
                .containsEntry("recipient", COURIER)
                .containsEntry("currency", PATH_USD);
    }

    @Test
    void anInvalidQuoteRequestIsRejectedBeforeAnyPayment() {
        assertThat(mvc.get().uri("/quotes/premium" + QUERY.replace("=2", "=0")))
                .hasStatus(400);
    }

    @Test
    void aLostPaidResponseIsRecoveredWithTheOriginalReceipt() {
        Credential credential = paidCredential();
        redemptions.save(redemption(credential, "order-7781"));

        MvcTestResult retry = mvc.get().uri("/quotes/premium" + QUERY)
                .header("Authorization", credential.toAuthorization())
                .header("Idempotency-Key", "order-7781")
                .exchange();

        assertThat(retry).hasStatusOk()
                .hasHeader("Payment-Receipt", "original-receipt")
                .hasHeader("Cache-Control", "private");
        assertThat(retry).bodyJson().extractingPath("$.price").isEqualTo(8.60);
    }

    @Test
    void aRedeemedHashCannotBuyADifferentRequest() {
        Credential credential = paidCredential();
        redemptions.save(redemption(credential, "order-7781"));

        MvcTestResult other = mvc.get()
                .uri("/quotes/premium?from=London&to=Leeds&weightKg=2")
                .header("Authorization", credential.toAuthorization())
                .header("Idempotency-Key", "order-7781")
                .exchange();

        assertThat(other).hasStatus(402).hasHeader("Cache-Control", "no-store")
                .doesNotContainHeader("Payment-Receipt");
        assertThat(other).bodyJson().extractingPath("$.type")
                .isEqualTo("https://paymentauth.org/problems/verification-failed");
        assertThat(challengeFrom(other).id()).isNotEqualTo(credential.challenge().id());
    }

    @Test
    void aRedeemedHashWithoutTheIdempotencyKeyIsAReplay() {
        Credential credential = paidCredential();
        redemptions.save(redemption(credential, "order-7781"));

        assertThat(mvc.get().uri("/quotes/premium" + QUERY)
                .header("Authorization", credential.toAuthorization()))
                .hasStatus(402);
    }

    private Credential paidCredential() {
        Challenge challenge =
                challengeFrom(mvc.get().uri("/quotes/premium" + QUERY).exchange());
        return new Credential(challenge.toEcho(),
                Map.of("type", "hash", "hash", paidHash), null);
    }

    private Redemption redemption(Credential credential, String idempotencyKey) {
        QuoteRequest request =
                new QuoteRequest("London", "Manchester", new BigDecimal("2"));
        QuoteResponse quote = new QuoteResponse("Swift Courier Premium",
                new BigDecimal("8.60"), "GBP", 1);
        return new Redemption(paidHash, credential.challenge().id(), idempotencyKey,
                request.fingerprint(), quote, "original-receipt", Instant.now());
    }

    private static Challenge challengeFrom(MvcTestResult result) {
        String header = result.getResponse().getHeader("WWW-Authenticate");
        assertThat(header).startsWith("Payment ");
        return Challenge.fromWwwAuthenticate(header).getFirst();
    }

    @Test
    void theRequestFieldIsBase64UrlWithoutPadding() {
        Challenge challenge = challengeFrom(
                mvc.get().uri("/quotes/premium" + QUERY).exchange());
        assertThat(challenge.requestB64()).doesNotContain("=");
        assertThat(new String(Base64.getUrlDecoder().decode(challenge.requestB64())))
                .contains("\"chainId\":42431");
    }
}
