package com.saas.notification.service;

/** Marks an email failure so the Kafka consumer can retry while retaining the delivery error. */
public class NotificationDeliveryException extends RuntimeException {
    /** Preserves the delivery failure cause so Kafka can retry while logs retain the original error. */
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}