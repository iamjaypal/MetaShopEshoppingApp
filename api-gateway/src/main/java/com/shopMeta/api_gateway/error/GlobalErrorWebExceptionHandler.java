package com.shopMeta.api_gateway.error;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import io.netty.channel.ConnectTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Global error handler for errors raised <em>by the gateway itself</em> (spec 6.2: "global error
 * JSON"): an unmatched path, an unreachable downstream service, a downstream timeout, ...
 *
 * <p>It replaces Spring Boot's default reactive error handler so that every gateway-generated
 * error uses the same shape as the filter errors (see {@link GatewayErrorWriter}).
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();

        if (ex instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (status == HttpStatus.NOT_FOUND) {
                log.warn("No gateway route for {} {}", exchange.getRequest().getMethod(), path);
                return errorWriter.write(exchange, status, "ROUTE_NOT_FOUND",
                        "No route found for this request");
            }
            if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                log.warn("Downstream service unavailable for {} {}", exchange.getRequest().getMethod(), path);
                return errorWriter.write(exchange, status, "SERVICE_UNAVAILABLE",
                        "The requested service is temporarily unavailable. Please try again later.");
            }
            return errorWriter.write(exchange, status, status.name(), statusException.getReason() != null
                    ? statusException.getReason()
                    : status.getReasonPhrase());
        }

        if (ex instanceof ConnectException || ex instanceof ConnectTimeoutException
                || ex instanceof TimeoutException) {
            log.warn("Downstream connection problem for {}: {}", path, ex.getMessage());
            return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The requested service is temporarily unavailable. Please try again later.");
        }

        log.error("Unhandled gateway error for {} {}", exchange.getRequest().getMethod(), path, ex);
        return errorWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred");
    }

}
