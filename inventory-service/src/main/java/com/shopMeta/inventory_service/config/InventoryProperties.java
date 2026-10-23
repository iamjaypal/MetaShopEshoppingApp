package com.shopMeta.inventory_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Stock rules (spec 9).
 */
@Component
@ConfigurationProperties(prefix = "metashop.inventory")
@Getter
@Setter
public class InventoryProperties {

    /** Threshold applied when a stock row is created without an explicit one. */
    private int defaultLowStockThreshold = 5;

    /**
     * How long a RESERVED hold survives without being confirmed or released. The sweeper releases
     * anything older than this (spec 9.4: 15 minutes).
     */
    private Duration reservationTtl = Duration.ofMinutes(15);

    /** How often the expiry sweeper runs (spec 9.4: every minute). */
    private Duration sweepInterval = Duration.ofSeconds(60);

    /**
     * The public endpoint reports at most this many units, so exact stock levels are not leaked
     * ("only 3 left" rather than "312 left", spec 9.3).
     */
    private int displayQuantityCap = 10;
}
