package com.codewithsam.courier.wallet;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON-RPC 2.0 over plain HTTP to a Tempo node. */
public class TempoRpc {

    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public TempoRpc(String rpcUrl) {
        this.endpoint = URI.create(rpcUrl);
    }

    public JsonNode call(String method, Object... params) {
        String body = json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1,
                "method", method, "params", List.of(params)));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode reply = json.readTree(send(request));
        if (reply.has("error")) {
            throw new TempoRpcException(method + " failed: " + reply.get("error"));
        }
        return reply.get("result");
    }

    public BigInteger quantity(String method, Object... params) {
        return Numeric.decodeQuantity(call(method, params).asString());
    }

    /** TIP-20 balanceOf. eth_getBalance means nothing on Tempo: no native token. */
    public BigInteger balanceOf(String token, String owner) {
        String data = TempoCalls.balanceOf(owner);
        String result = call("eth_call", Map.of("to", token, "data", data), "latest")
                .asString();
        return Numeric.toBigInt(result);
    }

    /** Testnet only: asks the node's faucet to send test stablecoins to an address. */
    public JsonNode fundAddress(String address) {
        return call("tempo_fundAddress", address);
    }

    private String send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new TempoRpcException("RPC unreachable: " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TempoRpcException("interrupted", e);
        }
    }
}
