package com.codewithsam.courier.payment;

import static com.codewithsam.courier.payment.TestChallenges.COURIER;
import static com.codewithsam.courier.payment.TestChallenges.NOW;
import static com.codewithsam.courier.payment.TestChallenges.PATH_USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.codewithsam.courier.wallet.TempoNetwork;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Receipt;
import java.math.BigInteger;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/** The client the break chapter uses. These tests pin down what goes wrong. */
class UnsafePaymentClientTest {

    private static final URI QUOTE = URI.create("http://courier.test/quotes/premium");

    private MockRestServiceServer courier;
    private FakeWallet wallet;
    private UnsafePaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder http = RestClient.builder();
        courier = MockRestServiceServer.bindTo(http).build();
        wallet = new FakeWallet();
        client = new UnsafePaymentClient(new CourierHttp(http.build()),
                new TempoChargePayer(wallet, TempoNetwork.MODERATO),
                new PaymentAuditService(TestDatabase.fresh(),
                        Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void paysWhateverTheCourierAsks() {
        courier.expect(requestTo(QUOTE)).andRespond(paymentRequired("5000000"));
        courier.expect(requestTo(QUOTE)).andRespond(paid("0x%064x".formatted(1)));

        client.get(QUOTE);

        assertThat(wallet.signed).singleElement()
                .satisfies(t -> assertThat(t.amount())
                        .isEqualTo(BigInteger.valueOf(5_000_000)));
    }

    @Test
    void paysTwiceWhenThePaidResponseIsLost() {
        courier.expect(requestTo(QUOTE)).andRespond(paymentRequired("50000"));
        courier.expect(requestTo(QUOTE)).andRespond(withServerError());
        courier.expect(requestTo(QUOTE)).andRespond(paymentRequired("50000"));
        courier.expect(requestTo(QUOTE)).andRespond(paid("0x%064x".formatted(2)));

        client.get(QUOTE);

        assertThat(wallet.transfersOnChain()).isEqualTo(2);
    }

    private static ResponseCreator paymentRequired(String units) {
        Challenge challenge = TestChallenges.charge("tempo", units, PATH_USD, COURIER,
                42431, NOW.plusSeconds(300));
        return withStatus(HttpStatus.PAYMENT_REQUIRED)
                .header("WWW-Authenticate", challenge.toWwwAuthenticate());
    }

    private static ResponseCreator paid(String txHash) {
        return withSuccess("{}", MediaType.APPLICATION_JSON)
                .header("Payment-Receipt",
                        Receipt.success(txHash, "tempo").toPaymentReceipt());
    }
}
