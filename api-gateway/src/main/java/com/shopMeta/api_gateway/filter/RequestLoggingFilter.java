package com.shopMeta.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Logs every routed request with its correlation id, status and duration (spec 5.5).
 *
 * <p>Runs immediately after {@link CorrelationIdFilter} (which is a {@link org.springframework.web.server.WebFilter}
 * and therefore already ran) so the id is always available.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 110;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        final String method = exchange.getRequest().getMethod().name();
        final String path = exchange.getRequest().getURI().getPath();
        final Object correlationId = exchange.getAttributes().get(CorrelationIdFilter.CORRELATION_ID_ATTR);
        final long startedAt = System.currentTimeMillis();

        log.info("--> {} {} [cid={}]", method, path, correlationId);

        return chain.filter(exchange)
                .doFinally(signal -> {
                    var statusCode = exchange.getResponse().getStatusCode();
                    HttpStatus status = statusCode != null ? HttpStatus.valueOf(statusCode.value()) : null;
                    log.info("<-- {} {} -> {} ({} ms) [cid={}]", method, path,
                            status != null ? status.value() : "?",
                            System.currentTimeMillis() - startedAt, correlationId);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

}
