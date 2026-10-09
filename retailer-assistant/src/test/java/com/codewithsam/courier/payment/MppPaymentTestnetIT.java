package com.codewithsam.courier.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewithsam.courier.config.PaymentPolicyProperties;
import com.codewithsam.courier.wallet.EnvFile;
import com.codewithsam.courier.wallet.PathUsd;
import com.codewithsam.courier.wallet.TempoNetwork;
import com.codewithsam.courier.wallet.TempoRpc;
import com.codewithsam.courier.wallet.TempoWallet;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Credential;
import com.stripe.mpp.Receipt;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Real payments on Tempo Moderato testnet (chain 42431), test tokens only.
 * Run with: ./mvnw verify -Ptestnet (needs .env from bin/new-wallet.sh).
 */
@Tag("testnet")
class MppPaymentTestnetIT {

    private static final String PREMIUM =
            "/quotes/premium?from=London&to=Manchester&weightKg=";

    private final EnvFile env = new EnvFile(Path.of("../.env"));
    private final TempoNetwork network = TempoNetwork.MODERATO;
    private final TempoRpc rpc = new TempoRpc(network.rpcUrl());
    private final String courierAddress =
            env.get("COURIER_RECIPIENT_ADDRESS").orElseThrow();
    private final Map<String, String> courierEnv = Map.of(
            "COURIER_RECIPIENT_ADDRESS", courierAddress,
            "MPP_SECRET_KEY", env.get("MPP_SECRET_KEY").orElseThrow());

    private TempoWallet wallet;
    private CourierHttp http;
    private PaymentAuditService audit;
    private MppPaymentClient safeClient;
    private UnsafePaymentClient unsafeClient;

    @BeforeEach
    void setUp() {
        wallet = new TempoWallet(rpc, network.chainId(),
                env.get("PAYER_PRIVATE_KEY").orElseThrow());
        http = new CourierHttp(RestClient.create());
        var db = TestDatabase.fresh();
        Clock clock = Clock.systemUTC();
        audit = new PaymentAuditService(db, clock);
        TempoChargePayer payer = new TempoChargePayer(wallet, network);
        var policy = new PaymentPolicyService(new PaymentPolicyProperties(
                new BigDecimal("0.10"), new BigDecimal("0.25"), List.of(courierAddress),
                PathUsd.ADDRESS, network.chainId()), audit, clock);
        safeClient = new MppPaymentClient(http, policy, payer,
                new PaymentOperationStore(db, clock), audit);
        unsafeClient = new UnsafePaymentClient(http, payer, audit);
    }

    @Test
    void freeQuoteThen402ThenPaymentThen200WithAReceipt() throws Exception {
        try (CourierProcess courier = CourierProcess.start(18190, courierEnv)) {
            assertThat(http.get(courier.uri("/quotes/standard?from=London&to=Manchester"
                    + "&weightKg=2"), null, null).status()).isEqualTo(200);
            HttpReply challenge = http.get(courier.uri(PREMIUM + "2"), null, null);
            assertThat(challenge.status()).isEqualTo(402);
            assertThat(challenge.headers().getCacheControl()).isEqualTo("no-store");
            BigDecimal before = courierBalance();

            PaidResponse paid =
                    safeClient.get(courier.uri(PREMIUM + "2"), "it-happy-path");

            assertThat(paid.body()).contains("Swift Courier Premium");
            assertThat(paid.txHash()).startsWith("0x");
            assertThat(wallet.isSettled(paid.txHash())).isTrue();
            assertThat(courierBalance().subtract(before)).isEqualByComparingTo("0.05");
            System.out.println("happy path " + network.transactionUrl(paid.txHash()));
        }
    }

    @Test
    void aPolicyRefusalBroadcastsNothing() throws Exception {
        try (CourierProcess courier = CourierProcess.start(18191, courierEnv,
                "--courier.premium.price=5.00")) {
            BigInteger nonceBefore = payerNonce();
            BigDecimal balanceBefore = payerBalance();

            assertThatThrownBy(
                    () -> safeClient.get(courier.uri(PREMIUM + "2"), "it-refusal"))
                    .isInstanceOf(PaymentRefusedException.class)
                    .hasMessage("5.00 pathUSD is over the 0.10 per-request limit");

            assertThat(payerNonce()).isEqualTo(nonceBefore);
            assertThat(payerBalance()).isEqualByComparingTo(balanceBefore);
        }
    }

    @Test
    void theUnsafeClientPaysTheRaisedPrice() throws Exception {
        try (CourierProcess courier = CourierProcess.start(18192, courierEnv,
                "--courier.premium.price=5.00")) {
            BigDecimal before = courierBalance();

            PaidResponse paid = unsafeClient.get(courier.uri(PREMIUM + "2"));

            assertThat(courierBalance().subtract(before)).isEqualByComparingTo("5.00");
            System.out.println("overpaid tx " + network.transactionUrl(paid.txHash()));
        }
    }

    @Test
    void aLostResponseCostsTheUnsafeClientTwoTransfersAndTheSafeClientOne()
            throws Exception {
        try (CourierProcess courier = CourierProcess.start(18193, courierEnv,
                "--courier.misbehaviour.lose-paid-responses=1")) {
            BigDecimal before = courierBalance();
            unsafeClient.get(courier.uri(PREMIUM + "3"));
            assertThat(courierBalance().subtract(before)).isEqualByComparingTo("0.10");
            assertThat(audit.entries()).filteredOn(e -> e.client().equals("unsafe"))
                    .hasSize(2);
        }
        try (CourierProcess courier = CourierProcess.start(18194, courierEnv,
                "--courier.misbehaviour.lose-paid-responses=1")) {
            BigDecimal before = courierBalance();

            PaidResponse paid = safeClient.get(courier.uri(PREMIUM + "3"), "it-lost");

            assertThat(paid.body()).contains("Swift Courier Premium");
            assertThat(courierBalance().subtract(before)).isEqualByComparingTo("0.05");
            assertThat(audit.entries()).filteredOn(e -> e.client().equals("safe"))
                    .extracting(PaymentAuditEntry::outcome)
                    .containsExactly(AuditOutcome.APPROVED, AuditOutcome.PAID,
                            AuditOutcome.RESPONSE_LOST, AuditOutcome.DELIVERED);
            System.out.println("recovered tx " + network.transactionUrl(paid.txHash()));
        }
    }

    @Test
    void aRedeemedTransactionCannotBuyADifferentRequest() throws Exception {
        try (CourierProcess courier = CourierProcess.start(18195, courierEnv)) {
            HttpReply first = http.get(courier.uri(PREMIUM + "4"), null, "it-replay");
            Challenge challenge = TempoChargePayer.challengeFrom(first);
            TempoChargePayer payer = new TempoChargePayer(wallet, network);
            var transfer = payer.sign(challenge);
            payer.settle(transfer);
            String credential = payer.authorization(challenge, transfer.hash());

            HttpReply paid =
                    http.get(courier.uri(PREMIUM + "4"), credential, "it-replay");
            assertThat(paid.status()).isEqualTo(200);
            String receipt = paid.headers().getFirst("Payment-Receipt");
            assertThat(Receipt.fromPaymentReceipt(receipt).reference())
                    .isEqualTo(transfer.hash());

            HttpReply sameRequestAgain =
                    http.get(courier.uri(PREMIUM + "4"), credential, "it-replay");
            HttpReply otherParcel =
                    http.get(courier.uri(PREMIUM + "9"), credential, "it-replay");
            HttpReply noIdempotencyKey =
                    http.get(courier.uri(PREMIUM + "4"), credential, null);

            assertThat(sameRequestAgain.status()).isEqualTo(200);
            assertThat(otherParcel.status()).isEqualTo(402);
            assertThat(otherParcel.body()).contains("verification-failed")
                    .contains("already redeemed");
            assertThat(noIdempotencyKey.status()).isEqualTo(402);
            assertThat(Credential.fromAuthorization(credential).payload())
                    .isEqualTo(Map.of("type", "hash", "hash", transfer.hash()));
        }
    }

    private BigDecimal courierBalance() {
        return PathUsd.fromBaseUnits(rpc.balanceOf(PathUsd.ADDRESS, courierAddress));
    }

    private BigDecimal payerBalance() {
        return PathUsd.fromBaseUnits(rpc.balanceOf(PathUsd.ADDRESS, wallet.address()));
    }

    private BigInteger payerNonce() {
        return rpc.quantity("eth_getTransactionCount", wallet.address(), "pending");
    }
}
