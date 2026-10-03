package com.saas.billing.service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InvoiceScheduler {
    private static final Logger logger = LoggerFactory.getLogger(InvoiceScheduler.class);
    private final InvoiceService invoiceService;

    public InvoiceScheduler(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Generates the prior UTC calendar month's invoice for every billing tenant. */
    @Scheduled(cron = "${billing.invoice-cron}")
    public void generatePreviousMonthInvoices() {
        String period = YearMonth.now(ZoneOffset.UTC).minusMonths(1).toString();
        for (var tenantId : invoiceService.tenantIds()) {
            try {
                invoiceService.generate(tenantId, period);
            } catch (RuntimeException exception) {
                logger.error("Could not generate invoice for tenant {} and period {}", tenantId, period, exception);
            }
        }
    }
}