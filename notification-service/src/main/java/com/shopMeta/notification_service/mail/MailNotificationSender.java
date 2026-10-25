package com.shopMeta.notification_service.mail;

import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.exception.NotificationDeliveryException;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sends one HTML email (spec 13).
 *
 * <p>Deliberately attempts the delivery exactly once. Retrying is the Kafka error handler's job —
 * three attempts with exponential backoff, then {@code <topic>.DLT} (spec 14.4) — because that also
 * survives a process restart, which an in-method retry loop does not.
 *
 * <p>Every failure surfaces as {@link NotificationDeliveryException} so callers have one thing to
 * catch, whether the message could not be built or the mail server refused it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailNotificationSender {

    private final JavaMailSender javaMailSender;

    private final NotificationProperties properties;

    /**
     * @throws NotificationDeliveryException if the message could not be built or the mail server
     *                                       rejected it
     */
    public void send(String to, String subject, String htmlBody) {

        MimeMessage message = javaMailSender.createMimeMessage();

        try {
            // multipart=true: an HTML body plus a plain-text alternative could be added later without
            // changing the call sites.
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(properties.getFromAddress(), properties.getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new NotificationDeliveryException(
                    "Could not build the email for " + to + ": " + ex.getMessage(), ex);
        }

        try {
            javaMailSender.send(message);
        } catch (MailException ex) {
            log.warn("Mail server rejected the message for {}: {}", to, ex.getMessage());
            throw new NotificationDeliveryException(
                    "Mail server rejected the message for " + to + ": " + ex.getMessage(), ex);
        }
    }
}
