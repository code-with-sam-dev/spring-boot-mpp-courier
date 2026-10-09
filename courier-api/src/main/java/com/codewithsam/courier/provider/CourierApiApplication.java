package com.codewithsam.courier.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CourierApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierApiApplication.class, args);
    }
}
