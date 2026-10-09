package com.codewithsam.courier.config;

import com.codewithsam.courier.payment.CourierHttp;
import com.codewithsam.courier.payment.MppPaymentClient;
import com.codewithsam.courier.payment.PaymentAuditService;
import com.codewithsam.courier.payment.PaymentOperationStore;
import com.codewithsam.courier.payment.PaymentPolicyService;
import com.codewithsam.courier.payment.TempoChargePayer;
import com.codewithsam.courier.payment.UnsafePaymentClient;
import com.codewithsam.courier.wallet.TempoNetwork;
import com.codewithsam.courier.wallet.TempoRpc;
import com.codewithsam.courier.wallet.TempoWallet;
import com.codewithsam.courier.wallet.Wallet;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class PaymentConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TempoNetwork tempoNetwork() {
        return TempoNetwork.MODERATO;
    }

    /** The private key comes from .env (gitignored, mode 600), never from this repo. */
    @Bean
    Wallet payerWallet(TempoNetwork network,
            @Value("${assistant.payment.payer-private-key}") String privateKey) {
        TempoRpc rpc = new TempoRpc(network.rpcUrl());
        return new TempoWallet(rpc, network.chainId(), privateKey);
    }

    @Bean
    TempoChargePayer tempoChargePayer(Wallet payerWallet, TempoNetwork network) {
        return new TempoChargePayer(payerWallet, network);
    }

    @Bean
    CourierHttp courierHttp(RestClient.Builder builder, CourierProperties courier) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(courier.readTimeout());
        return new CourierHttp(builder.requestFactory(requests).build());
    }

    @Bean
    MppPaymentClient mppPaymentClient(CourierHttp courierHttp,
            PaymentPolicyService policy, TempoChargePayer payer,
            PaymentOperationStore operations, PaymentAuditService audit) {
        return new MppPaymentClient(courierHttp, policy, payer, operations, audit);
    }

    /** Only with --spring.profiles.active=break, for the chapter on failures. */
    @Bean
    @Profile("break")
    UnsafePaymentClient unsafePaymentClient(CourierHttp courierHttp,
            TempoChargePayer payer, PaymentAuditService audit) {
        return new UnsafePaymentClient(courierHttp, payer, audit);
    }
}
