package com.shopMeta.api_gateway.filter;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Generates the correlation id for every request (spec 5.5).
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured (so a caller or another gateway can
 * propagate one), otherwise a UUID is generated. The value is exposed as a request attribute, echoed
 * in the response header and forwarded downstream so every service logs the same id.
 *
 * <p>Implemented as a {@link WebFilter} rather than a gateway {@code GlobalFilter} on purpose: it
 * must also run for requests that match <em>no</em> route, otherwise a 404 from the gateway would
 * have no {@code traceId} in its error body.
 */
@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTR = "metashop.correlationId";

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

}
