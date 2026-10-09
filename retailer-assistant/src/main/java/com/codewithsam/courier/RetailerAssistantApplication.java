package com.codewithsam.courier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RetailerAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailerAssistantApplication.class, args);
    }
}
