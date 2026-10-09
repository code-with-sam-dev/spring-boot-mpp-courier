package com.codewithsam.courier.payment;

import static com.codewithsam.courier.payment.TestChallenges.COURIER;
import static com.codewithsam.courier.payment.TestChallenges.NOW;
import static com.codewithsam.courier.payment.TestChallenges.PATH_USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewithsam.courier.config.PaymentPolicyProperties;
import com.codewithsam.courier.wallet.TempoNetwork;
import com.codewithsam.courier.wallet.TempoRpcException;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Credential;
import com.stripe.mpp.Receipt;
import com.stripe.mpp.methods.tempo.Attribution;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class MppPaymentClientTest {

    private static final URI QUOTE = URI.create(
            "http://courier.test/quotes/premium?from=London&to=Manchester&weightKg=2");
    private static final String BODY = "{\"courier\":\"Swift Courier Premium\","
            + "\"price\":8.60,\"currency\":\"GBP\",\"days\":1}";
    private static final String FIRST_TX = "0x%064x".formatted(1);

    private MockRestServiceServer courier;
    private FakeWallet wallet;
    private PaymentAuditService audit;
    private MppPaymentClient client;
    private final List<ClientHttpRequest> paidRequests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder http = RestClient.builder();
        courier = MockRestServiceServer.bindTo(http).build();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var db = TestDatabase.fresh();
        wallet = new FakeWallet();
        audit = new PaymentAuditService(db, clock);
        var policy = new PaymentPolicyService(new PaymentPolicyProperties(
                new BigDecimal("0.10"), new BigDecimal("0.25"), List.of(COURIER),
                PATH_USD, 42431), audit, clock);
        client = new MppPaymentClient(new CourierHttp(http.build()), policy,
                new TempoChargePayer(wallet, TempoNetwork.MODERATO),
                new PaymentOperationStore(db, clock), audit);
    }

    @Test
    void aFreeResponseCostsNothing() {
        courier.expect(requestTo(QUOTE))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        PaidResponse response = client.get(QUOTE, "order-1");

        assertThat(response.amountPaid()).isZero();
        assertThat(wallet.signed).isEmpty();
    }

    @Test
    void paysOnceThenRetriesWithAHashCredential() {
        Challenge challenge = TestChallenges.premiumQuote();
        courier.expect(requestTo(QUOTE)).andRespond(paymentRequired(challenge));
        courier.expect(requestTo(QUOTE)).andRespond(paid(FIRST_TX));

        PaidResponse response = client.get(QUOTE, "order-1");

        assertThat(response.body()).isEqualTo(BODY);
        assertThat(response.amountPaid()).isEqualByComparingTo("0.05");
        assertThat(response.txHash()).isEqualTo(FIRST_TX);
        assertThat(wallet.signed).singleElement().satisfies(transfer -> {
            assertThat(transfer.recipient()).isEqualTo(COURIER);
            assertThat(transfer.amount()).isEqualTo(BigInteger.valueOf(50_000));
            String memo = transfer.memo();
            assertThat(Attribution.verifyChallengeBinding(memo, challenge.id()))
                    .isTrue();
        });
        Credential sent = credentialOf(paidRequests.getFirst());
        assertThat(sent.payload()).isEqualTo(Map.of("type", "hash", "hash", FIRST_TX));
        assertThat(sent.challenge().id()).isEqualTo(challenge.id());
        assertThat(sent.source())
                .isEqualTo("did:pkh:eip155:42431:" + wallet.address());
        assertThat(audit.entries()).extracting(PaymentAuditEntry::outcome)
                .containsExactly(AuditOutcome.APPROVED, AuditOutcome.PAID,
                        AuditOutcome.DELIVERED);
    }

    @Test
    void aPolicyRefusalBroadcastsNothing() {
        courier.expect(requestTo(QUOTE))
                .andRespond(paymentRequired(TestChallenges.charge("tempo", "5000000",
                        PATH_USD, COURIER, 42431, NOW.plusSeconds(300))));

        assertThatThrownBy(() -> client.get(QUOTE, "order-1"))
                .isInstanceOf(PaymentRefusedException.class)
                .hasMessage("5.00 pathUSD is over the 0.10 per-request limit");
        assertThat(wallet.signed).isEmpty();
        assertThat(wallet.broadcasts).isEmpty();
    }

    @Test
    void aLostResponseIsRecoveredWithExactlyOneTransfer() {
        courier.expect(requestTo(QUOTE)).andRespond(premiumChallenge());
        courier.expect(requestTo(QUOTE)).andRespond(capture(withServerError()));
        courier.expect(requestTo(QUOTE)).andRespond(paid(FIRST_TX));

        PaidResponse response = client.get(QUOTE, "order-1");

        assertThat(response.body()).isEqualTo(BODY);
        assertThat(wallet.transfersOnChain()).isEqualTo(1);
        assertThat(authorizationOf(paidRequests.get(1)))
                .isEqualTo(authorizationOf(paidRequests.get(0)));
        assertThat(idempotencyKeyOf(paidRequests.get(1)))
                .isEqualTo(idempotencyKeyOf(paidRequests.get(0)));
    }

    @Test
    void aTimeoutAfterPayingIsRecoveredTheSameWay() {
        courier.expect(requestTo(QUOTE)).andRespond(premiumChallenge());
        courier.expect(requestTo(QUOTE))
                .andRespond(capture(
                        withException(new SocketTimeoutException("timeout"))));
        courier.expect(requestTo(QUOTE)).andRespond(paid(FIRST_TX));

        client.get(QUOTE, "order-1");

        assertThat(wallet.transfersOnChain()).isEqualTo(1);
    }

    @Test
    void ifTheCourierWillNotHandItOverTheClientStopsAndNeverPaysAgain() {
        courier.expect(requestTo(QUOTE)).andRespond(premiumChallenge());
        courier.expect(requestTo(QUOTE)).andRespond(capture(withServerError()));
        courier.expect(requestTo(QUOTE)).andRespond(capture(
                paymentRequired(TestChallenges.premiumQuote())));

        assertThatThrownBy(() -> client.get(QUOTE, "order-1"))
                .isInstanceOf(PaidButUndeliveredException.class)
                .hasMessageContaining(FIRST_TX);

        // Asked again later: it resumes from the record, no new transfer is signed.
        courier.reset();
        paidRequests.clear();
        courier.expect(requestTo(QUOTE)).andRespond(paid(FIRST_TX));
        PaidResponse later = client.get(QUOTE, "order-1");

        assertThat(later.txHash()).isEqualTo(FIRST_TX);
        assertThat(wallet.signed).hasSize(1);
        assertThat(credentialOf(paidRequests.getFirst()).payload())
                .isEqualTo(Map.of("type", "hash", "hash", FIRST_TX));
    }

    @Test
    void aCrashBeforeTheBroadcastResumesTheSameSignedTransfer() {
        wallet.broadcastFailuresToSimulate = 1;
        courier.expect(requestTo(QUOTE)).andRespond(premiumChallenge());

        assertThatThrownBy(() -> client.get(QUOTE, "order-1"))
                .isInstanceOf(TempoRpcException.class);
        assertThat(wallet.transfersOnChain()).isZero();

        courier.reset();
        courier.expect(requestTo(QUOTE)).andRespond(paid(FIRST_TX));
        client.get(QUOTE, "order-1");

        assertThat(wallet.signed).hasSize(1);
        assertThat(wallet.broadcasts).containsExactly(FIRST_TX);
    }

    @Test
    void aRequestAlreadyRedeemedIsAnsweredFromTheRecord() {
        courier.expect(requestTo(QUOTE)).andRespond(premiumChallenge());
        courier.expect(requestTo(QUOTE)).andRespond(paid(FIRST_TX));
        client.get(QUOTE, "order-1");
        courier.verify();
        courier.reset();

        PaidResponse again = client.get(QUOTE, "order-1");

        courier.verify(); // no request reached the courier
        assertThat(again.body()).isEqualTo(BODY);
        assertThat(again.txHash()).isEqualTo(FIRST_TX);
        assertThat(wallet.signed).hasSize(1);
    }

    private static ResponseCreator premiumChallenge() {
        return paymentRequired(TestChallenges.premiumQuote());
    }

    private static ResponseCreator paymentRequired(Challenge challenge) {
        return withStatus(HttpStatus.PAYMENT_REQUIRED)
                .header("WWW-Authenticate", challenge.toWwwAuthenticate())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"type\":\"https://paymentauth.org/problems/"
                        + "payment-required\"}");
    }

    private ResponseCreator paid(String txHash) {
        return capture(withSuccess(BODY, MediaType.APPLICATION_JSON)
                .header("Payment-Receipt",
                        Receipt.success(txHash, "tempo").toPaymentReceipt()));
    }

    private ResponseCreator capture(ResponseCreator response) {
        return request -> {
            paidRequests.add((ClientHttpRequest) request);
            return response.createResponse(request);
        };
    }

    private static String authorizationOf(ClientHttpRequest request) {
        return request.getHeaders().getFirst("Authorization");
    }

    private static String idempotencyKeyOf(ClientHttpRequest request) {
        return request.getHeaders().getFirst("Idempotency-Key");
    }

    private static Credential credentialOf(ClientHttpRequest request) {
        return Credential.fromAuthorization(authorizationOf(request));
    }
}
