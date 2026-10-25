package com.shopMeta.notification_service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.exception.NotificationDeliveryException;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The SMTP boundary: what actually goes on the wire, and what happens when the server refuses it.
 */
class MailNotificationSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);

    private final NotificationProperties properties = new NotificationProperties();

    private final MailNotificationSender sender =
            new MailNotificationSender(javaMailSender, properties);

    @BeforeEach
    void stubMessageCreation() {
        given(javaMailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
    }

    @Test
    void send_addressesTheMessageAndPassesTheHtmlThrough() throws Exception {

        sender.send("asha@example.com", "Order MS-000042 confirmed", "<p>Hello Asha</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        then(javaMailSender).should().send(captor.capture());

        MimeMessage message = captor.getValue();

        assertThat(message.getSubject()).isEqualTo("Order MS-000042 confirmed");
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("asha@example.com");
        assertThat(message.getFrom()[0].toString())
                .contains("no-reply@metashop.com")
                .contains("MetaShop");
        assertThat(bodyOf(message)).isEqualTo("<p>Hello Asha</p>");
    }

    @Test
    void send_usesTheConfiguredFromAddressAndDisplayName() throws Exception {

        properties.setFromAddress("orders@metashop.com");
        properties.setFromName("MetaShop Orders");

        sender.send("asha@example.com", "Subject", "<p>Hi</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        then(javaMailSender).should().send(captor.capture());

        assertThat(captor.getValue().getFrom()[0].toString())
                .contains("orders@metashop.com")
                .contains("MetaShop Orders");
    }

    @Test
    void send_wrapsAMailServerRefusal() {

        willThrow(new MailSendException("Connection refused"))
                .given(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send("asha@example.com", "Subject", "<p>Hi</p>"))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("asha@example.com")
                .hasCauseInstanceOf(MailSendException.class);
    }

    /**
     * Reads the body back out of the message. {@code MimeMessageHelper} is created with
     * {@code multipart = true}, so the HTML can be nested one or more multiparts deep; this walks
     * whatever structure is there rather than depending on its exact shape.
     */
    private static String bodyOf(MimeMessage message) throws Exception {
        return extractText(message.getContent());
    }

    private static String extractText(Object content) throws Exception {

        if (content instanceof MimeMultipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                text.append(extractText(multipart.getBodyPart(i).getContent()));
            }
            return text.toString();
        }

        return content.toString();
    }
}
