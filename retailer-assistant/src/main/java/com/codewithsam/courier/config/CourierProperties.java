package com.codewithsam.courier.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("assistant.courier")
public record CourierProperties(
        @DefaultValue("http://localhost:8090") String baseUrl,
        @DefaultValue("30s") Duration readTimeout) {
}
