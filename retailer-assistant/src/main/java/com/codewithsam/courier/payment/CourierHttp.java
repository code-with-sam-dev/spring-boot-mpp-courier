package com.codewithsam.courier.payment;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** Plain GETs that hand back any status, 402 included, instead of throwing. */
public class CourierHttp {

    private final RestClient restClient;

    public CourierHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    public HttpReply get(URI uri, String authorization, String idempotencyKey) {
        return restClient.get().uri(uri)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                    if (idempotencyKey != null) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                })
                .exchange((request, response) -> new HttpReply(
                        response.getStatusCode().value(),
                        response.getHeaders(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }
}
