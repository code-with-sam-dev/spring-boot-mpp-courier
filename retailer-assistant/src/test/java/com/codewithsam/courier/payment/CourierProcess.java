package com.codewithsam.courier.payment;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The real courier-api jar, started as its own process, as it runs in production. */
final class CourierProcess implements AutoCloseable {

    private final Process process;
    final int port;

    private CourierProcess(Process process, int port) {
        this.process = process;
        this.port = port;
    }

    static CourierProcess start(int port, Map<String, String> env, String... properties)
            throws Exception {
        Path jar;
        try (var files = Files.list(Path.of("../courier-api/target"))) {
            jar = files.filter(p -> p.toString().endsWith("-exec.jar")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("build courier-api first"));
        }
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"), "-jar",
                jar.toString(), "--server.port=" + port,
                "--courier.premium.realm=localhost:" + port,
                "--spring.datasource.url=jdbc:h2:mem:courier"));
        command.addAll(List.of(properties));
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(new File("target/courier-" + port + ".log"));
        builder.environment().putAll(env);
        CourierProcess courier = new CourierProcess(builder.start(), port);
        courier.awaitReady();
        return courier;
    }

    URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + port + pathAndQuery);
    }

    private void awaitReady() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (int i = 0; i < 60; i++) {
            try {
                http.send(HttpRequest.newBuilder(uri("/quotes/standard?from=a&to=b&weightKg=1"))
                        .build(), HttpResponse.BodyHandlers.discarding());
                return;
            } catch (IOException notYet) {
                Thread.sleep(500);
            }
        }
        close();
        throw new IllegalStateException("courier-api did not start on " + port);
    }

    @Override
    public void close() {
        process.destroy();
    }
}
