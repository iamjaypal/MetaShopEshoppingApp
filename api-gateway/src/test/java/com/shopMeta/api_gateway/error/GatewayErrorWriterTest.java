package com.shopMeta.api_gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.api_gateway.filter.CorrelationIdFilter;

class GatewayErrorWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayErrorWriter writer = new GatewayErrorWriter(objectMapper);

    @Test
    @SuppressWarnings("unchecked")
    void writesTheSpec53ErrorShape() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/users").build());
        exchange.getAttributes().put(CorrelationIdFilter.CORRELATION_ID_ATTR, "cid-123");

        writer.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Nope").block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);

        Map<String, Object> body = objectMapper.readValue(
                exchange.getResponse().getBodyAsString().block(), Map.class);

        assertThat(body)
                .containsEntry("status", 403)
                .containsEntry("error", "FORBIDDEN")
                .containsEntry("message", "Nope")
                .containsEntry("path", "/api/v1/admin/users")
                .containsEntry("traceId", "cid-123");
        assertThat(body.get("timestamp")).isNotNull();
    }

    @Test
    void doesNothingWhenTheResponseIsAlreadyCommitted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products").build());
        exchange.getResponse().setComplete().block();

        writer.write(exchange, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "ignored").block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

}
