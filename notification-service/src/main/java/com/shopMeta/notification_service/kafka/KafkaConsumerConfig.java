package com.shopMeta.notification_service.kafka;

import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.exception.MalformedEventException;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * The retry and dead-letter policy shared by every listener in this service (spec 14.4).
 *
 * <p>Boot picks this {@code CommonErrorHandler} bean up and installs it on the auto-configured
 * listener container factory, so there is no need to hand-build a container factory here.
 *
 * <p>Three retries with exponential backoff (1s, 2s, 4s), then the record is published to
 * {@code <source-topic>.DLT} — the suffix is configured rather than hard-coded because
 * {@code DeadLetterPublishingRecoverer}'s own default is {@code -dlt}, while spec 14.1 specifies
 * {@code *.DLT}.
 *
 * <p>{@code commitRecovered} matters here: the listener uses manual acks, so a recovered record would
 * otherwise never have its offset committed and would be replayed on the next restart — sending the
 * same email a second time. With it enabled the recoverer commits the offset itself, straight on the
 * consumer, once the record is safely on the dead-letter topic.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            NotificationProperties properties) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.getDltSuffix(),
                        record.partition()));

        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(properties.getRetryMaxAttempts());
        backOff.setInitialInterval(properties.getRetryInitialInterval().toMillis());
        backOff.setMultiplier(properties.getRetryMultiplier());
        backOff.setMaxInterval(properties.getRetryMaxInterval().toMillis());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Commit the offset once the record has been dead-lettered, even though this listener uses
        // manual acks. Without this the record would come back after a restart and be sent again.
        errorHandler.setCommitRecovered(true);

        // A payload that does not fit its event type, or a record that cannot be parsed at all, will
        // fail identically on every attempt, so it goes straight to the dead-letter topic instead of
        // sitting through three rounds of backoff first. Only delivery failures are retried.
        errorHandler.addNotRetryableExceptions(MalformedEventException.class);

        return errorHandler;
    }
}
