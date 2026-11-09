package dev.jagadeesh.booking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Logs the message. An SMS/WhatsApp/email provider plugs in here. */
@Component
public class MessageSender {
    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    public void send(java.util.UUID customerId, String message) {
        log.info("MESSAGE to {}: {}", customerId, message);
    }
}
