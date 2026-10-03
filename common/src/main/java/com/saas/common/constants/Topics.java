package com.saas.common.constants;

/** Centralizes Kafka topic names shared by the platform's producers and consumers. */
public final class Topics {
    public static final String TENANT_EVENTS = "tenant-events";
    public static final String USAGE_EVENTS = "usage-events";
    public static final String USAGE_AGGREGATED = "usage-aggregated";
    public static final String INVOICE_EVENTS = "invoice-events";

    /** Prevents instantiation because this type only exposes constants. */
    private Topics() {
    }
}