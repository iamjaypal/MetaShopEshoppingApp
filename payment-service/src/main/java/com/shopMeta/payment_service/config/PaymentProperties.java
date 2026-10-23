package com.shopMeta.payment_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mock-gateway behaviour (spec 12). There is no real gateway behind the mock, so the mode decides
 * what a charge does: {@code SUCCESS} captures every attempt, {@code FAILURE} declines every
 * attempt (demo of the release path), {@code RANDOM} flips a coin.
 */
@Component
@ConfigurationProperties(prefix = "metashop.payment")
@Getter
@Setter
public class PaymentProperties {

    private GatewayMode gatewayMode = GatewayMode.SUCCESS;

    public enum GatewayMode {
        SUCCESS, FAILURE, RANDOM
    }
}
