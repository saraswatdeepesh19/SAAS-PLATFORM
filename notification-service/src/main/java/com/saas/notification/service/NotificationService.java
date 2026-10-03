package com.saas.notification.service;

import com.saas.common.events.InvoiceGeneratedEvent;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final JdbcTemplate jdbcTemplate;
    private final EmailChannel emailChannel;
    private final String mailFrom;

    public NotificationService(
            JdbcTemplate jdbcTemplate,
            EmailChannel emailChannel,
            @Value("${notification.mail-from}") String mailFrom) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailChannel = emailChannel;
        this.mailFrom = mailFrom;
    }

    /**
     * Deduplicates invoice notifications, sends the email, and records delivery status.
     * Keeping FAILED status on delivery exceptions allows Kafka retries to retry delivery without losing the log.
     */
    @Transactional(noRollbackFor = NotificationDeliveryException.class)
    public void notify(InvoiceGeneratedEvent event) {
        String subject = "Invoice " + event.invoiceNumber() + " for " + event.period();
        var notificationIds = jdbcTemplate.query(
                "INSERT INTO notification_log (id, event_id, tenant_id, channel, recipient, subject, status) "
                        + "VALUES (?, ?, ?, 'EMAIL', ?, ?, 'PENDING') "
                        + "ON CONFLICT (event_id) DO UPDATE SET status = 'PENDING', error = NULL, "
                        + "recipient = EXCLUDED.recipient, subject = EXCLUDED.subject "
                        + "WHERE notification_log.status <> 'SENT' RETURNING id",
                (resultSet, row) -> resultSet.getObject(1, UUID.class),
                UUID.randomUUID(), event.eventId(), event.tenantId(), event.recipientEmail(), subject);
        if (notificationIds.isEmpty()) {
            logger.debug("Previously delivered invoice event skipped eventId={} tenantId={}",
                    event.eventId(), event.tenantId());
            return;
        }

        UUID logId = notificationIds.getFirst();
        try {
            emailChannel.send(event);
            jdbcTemplate.update("UPDATE notification_log SET status = 'SENT', error = NULL WHERE id = ?", logId);
                logger.info("Invoice email delivered eventId={} tenantId={} notificationId={}",
                    event.eventId(), event.tenantId(), logId);
        } catch (RuntimeException exception) {
            jdbcTemplate.update("UPDATE notification_log SET status = 'FAILED', error = ? WHERE id = ?",
                    truncate(exception.getMessage()), logId);
                logger.error("Invoice email delivery failed eventId={} tenantId={} notificationId={}",
                    event.eventId(), event.tenantId(), logId, exception);
            throw new NotificationDeliveryException("Could not deliver invoice email", exception);
        }
    }

    /** Bounds stored provider error text to fit the notification log column. */
    private String truncate(String message) {
        if (message == null) {
            return "Email delivery failed";
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}