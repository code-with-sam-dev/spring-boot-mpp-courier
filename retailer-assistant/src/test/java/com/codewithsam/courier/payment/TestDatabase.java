package com.codewithsam.courier.payment;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

final class TestDatabase {

    private TestDatabase() {
    }

    static JdbcClient fresh() {
        return JdbcClient.create(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName(UUID.randomUUID().toString())
                .addScript("schema.sql")
                .build());
    }
}
