package com.shopMeta.notification_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Notification rules (spec 13).
 */
@Component
@ConfigurationProperties(prefix = "metashop.notification")
@Getter
@Setter
public class NotificationProperties {

    /** The {@code From:} address every email is sent with. */
    private String fromAddress = "no-reply@metashop.com";

    /** The {@code From:} display name every email is sent with. */
    private String fromName = "MetaShop";

    /**
     * Where operational alerts go. {@code InventoryLowEvent} has no owning user, so it is sent to
     * the platform operators instead.
     */
    private String adminEmail = "admin@metashop.com";

    /**
     * Where the Angular app is served from. Used to build the links inside the emails (password reset,
     * "view your order"), which must point at the browser-facing app rather than at this service.
     */
    private String frontendBaseUrl = "http://localhost:4200";

    /** Upper bound for {@code ?size=} on the admin list, so one request cannot ask for everything. */
    private int maxPageSize = 100;

    /**
     * Suffix appended to the source topic for dead letters (spec 14.1). Kept configurable because it
     * is a broker/ops convention rather than something this service owns.
     */
    private String dltSuffix = ".DLT";

    /** How many times a failed event is retried before it is dead-lettered (spec 13: three). */
    private int retryMaxAttempts = 3;

    /** Delay before the first retry. Doubles on each subsequent attempt. */
    private Duration retryInitialInterval = Duration.ofSeconds(1);

    /** Ceiling on the delay between retries, so the backoff cannot grow without bound. */
    private Duration retryMaxInterval = Duration.ofSeconds(10);

    /** Growth factor applied to the retry delay. */
    private double retryMultiplier = 2.0;
}
