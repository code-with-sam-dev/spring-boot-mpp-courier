package com.codewithsam.courier.provider.payment;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What one premium quote costs and who gets paid. Only the courier's address is
 * here, never a private key: receiving money needs no key.
 */
@ConfigurationProperties("courier.premium")
public record PremiumChargeProperties(
        @DefaultValue("0.05") BigDecimal price,
        String recipient,
        @DefaultValue("localhost:8090") String realm,
        String secretKey,
        @DefaultValue("Premium courier quotation") String description) {
}
