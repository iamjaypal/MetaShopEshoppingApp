package com.shopMeta.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private ServerWebExchange run(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return forwarded.get();
    }

    @Test
    void generatesACorrelationIdWhenTheCallerDoesNotSendOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products").build());

        ServerWebExchange forwarded = run(exchange);

        String generated = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generated).isNotBlank();
        assertThat(forwarded.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(generated);
        assertThat(exchange.getAttributes()).containsEntry(CorrelationIdFilter.CORRELATION_ID_ATTR, generated);
    }

    @Test
    void honoursAnInboundCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "caller-supplied-id")
                        .build());

        ServerWebExchange forwarded = run(exchange);

        assertThat(forwarded.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("caller-supplied-id");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("caller-supplied-id");
    }

    @Test
    void blankInboundIdIsReplaced() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ")
                        .build());

        ServerWebExchange forwarded = run(exchange);

        assertThat(forwarded.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("   ");
    }

}
