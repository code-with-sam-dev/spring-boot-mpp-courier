package com.codewithsam.courier.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

// A real MCP client over streamable HTTP, no LLM involved: MCP does not need one.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        // A throwaway key (the number 1); this test never pays.
        "PAYER_PRIVATE_KEY=0x00000000000000000000000000000000"
                + "00000000000000000000000000000001",
        "COURIER_RECIPIENT_ADDRESS=0xe614e43e4fbf9867bdf4e2ddbd3f628658b93ddb",
        "spring.datasource.url=jdbc:h2:mem:mcp-test"})
class CourierAssistantMcpTest {

    @LocalServerPort
    int port;

    @Test
    void exposesTheThreeToolsOverMcp() {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port).build()).build();
        try (client) {
            client.initialize();
            List<McpSchema.Tool> tools = client.listTools().tools();

            assertThat(tools).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("standard_delivery_quotes",
                            "premium_delivery_quote", "payment_audit");
            assertThat(tools).filteredOn(t -> t.name().equals("premium_delivery_quote"))
                    .singleElement()
                    .extracting(McpSchema.Tool::description).asString()
                    .startsWith("COSTS MONEY");

            McpSchema.CallToolResult audit = client.callTool(
                    new McpSchema.CallToolRequest("payment_audit", java.util.Map.of()));
            assertThat(audit.isError()).isFalse();
        }
    }
}
