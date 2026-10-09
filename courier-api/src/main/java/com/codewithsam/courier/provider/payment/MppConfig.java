package com.codewithsam.courier.provider.payment;

import com.stripe.mpp.Mpp;
import com.stripe.mpp.methods.tempo.TempoDefaults;
import com.stripe.mpp.methods.tempo.TempoMethod;
import com.stripe.mpp.server.ChargeRequest;
import com.stripe.mpp.server.MppHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MppConfig {

    /** One instance for the whole app: its replay store must see every request. */
    @Bean
    TempoMethod tempo() {
        return TempoMethod.of().testnet().build();
    }

    @Bean
    MppHandler payments(TempoMethod tempo, PremiumChargeProperties charge) {
        return Mpp.create(tempo, charge.realm(), charge.secretKey());
    }

    @Bean
    ChargeRequest premiumQuoteCharge(TempoMethod tempo, PremiumChargeProperties charge) {
        return ChargeRequest.of(tempo.chargeIntent(), charge.price().toPlainString(),
                        TempoDefaults.TESTNET_PATH_USD, charge.recipient())
                .description(charge.description());
    }
}
