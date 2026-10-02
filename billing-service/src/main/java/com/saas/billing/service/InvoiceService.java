package com.saas.billing.service;

import com.saas.billing.dto.InvoiceLineResponse;
import com.saas.billing.dto.InvoiceResponse;
import com.saas.billing.dto.TenantPlanResponse;
import com.saas.billing.pricing.PricingLine;
import com.saas.billing.pricing.PricingResult;
import com.saas.billing.pricing.PricingStrategyFactory;
import com.saas.common.events.InvoiceGeneratedEvent;
import com.saas.common.enums.PlanType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvoiceService {
        private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter INVOICE_PERIOD = DateTimeFormatter.ofPattern("yyyyMM");
    private final JdbcTemplate jdbcTemplate;
    private final PricingStrategyFactory strategyFactory;
    private final ApplicationEventPublisher eventPublisher;

    public InvoiceService(
            JdbcTemplate jdbcTemplate,
            PricingStrategyFactory strategyFactory,
            ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.strategyFactory = strategyFactory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public InvoiceResponse generate(UUID tenantId, String period) {
        validatePeriod(period);
        InvoiceResponse existing = findInvoice(tenantId, period);
        if (existing != null) {
                        logger.info("Existing invoice returned tenantId={} period={} invoiceId={}",
                                        tenantId, period, existing.id());
            return existing;
        }
        TenantPlanResponse plan = getPlan(tenantId);
        Long totalSeconds = jdbcTemplate.query(
                "SELECT total_seconds FROM usage_monthly_snapshot WHERE tenant_id = ? AND period = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L, tenantId, period);
        long usageMinutes = totalSeconds / 60 + (totalSeconds % 60 == 0 ? 0 : 1);
        PricingResult pricing = strategyFactory.forPlan(plan.planType()).calculate(usageMinutes);
        UUID invoiceId = UUID.randomUUID();
        String invoiceNumber = "INV-" + YearMonth.parse(period).format(INVOICE_PERIOD) + "-"
                + tenantId.toString().substring(0, 8).toUpperCase();
        Instant createdAt = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, tenant_id, invoice_number, period, total_amount, currency) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                invoiceId, tenantId, invoiceNumber, period, pricing.total(), plan.currency());
        for (PricingLine line : pricing.lines()) {
            jdbcTemplate.update(
                    "INSERT INTO invoice_lines (id, invoice_id, description, quantity, unit_price, amount) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), invoiceId, line.description(), line.quantity(), line.unitPrice(), line.amount());
        }
        eventPublisher.publishEvent(new InvoiceGeneratedEvent(
                UUID.randomUUID(), tenantId, invoiceId, invoiceNumber, period,
                pricing.total().setScale(2, RoundingMode.HALF_UP), plan.currency(), plan.billingEmail(), createdAt));
        logger.info("Invoice generated tenantId={} period={} invoiceId={} total={} currency={}",
                tenantId, period, invoiceId, pricing.total(), plan.currency());
        return findInvoice(tenantId, period);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(UUID tenantId) {
        return jdbcTemplate.query("SELECT * FROM invoices WHERE tenant_id = ? ORDER BY period DESC",
                        (resultSet, row) -> mapInvoiceRow(resultSet), tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID tenantId, UUID invoiceId) {
        InvoiceRow row = jdbcTemplate.query("SELECT * FROM invoices WHERE tenant_id = ? AND id = ?",
                        (resultSet, rowNum) -> mapInvoiceRow(resultSet), tenantId, invoiceId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return toResponse(row);
    }

    @Transactional(readOnly = true)
    public TenantPlanResponse getPlan(UUID tenantId) {
        return jdbcTemplate.query(
                        "SELECT tenant_id, tenant_name, billing_email, plan_type, currency FROM tenant_plans WHERE tenant_id = ?",
                        (resultSet, row) -> new TenantPlanResponse(resultSet.getObject("tenant_id", UUID.class),
                                        resultSet.getString("tenant_name"), resultSet.getString("billing_email"),
                                        PlanType.valueOf(resultSet.getString("plan_type")),
                                        resultSet.getString("currency").trim()),
                        tenantId)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Billing plan not found"));
    }

    @Transactional(readOnly = true)
    public List<UUID> tenantIds() {
        return jdbcTemplate.query("SELECT tenant_id FROM tenant_plans", (resultSet, row) -> resultSet.getObject(1, UUID.class));
    }

    private InvoiceResponse findInvoice(UUID tenantId, String period) {
        return jdbcTemplate.query("SELECT * FROM invoices WHERE tenant_id = ? AND period = ?",
                        (resultSet, row) -> mapInvoiceRow(resultSet), tenantId, period)
                .stream().findFirst().map(this::toResponse).orElse(null);
    }

    private InvoiceRow mapInvoiceRow(ResultSet resultSet) throws SQLException {
        return new InvoiceRow(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("invoice_number"), resultSet.getString("period").trim(),
                resultSet.getBigDecimal("total_amount"), resultSet.getString("currency").trim(),
                resultSet.getString("status"), resultSet.getTimestamp("created_at").toInstant());
    }

    private InvoiceResponse toResponse(InvoiceRow row) {
        List<InvoiceLineResponse> lines = jdbcTemplate.query(
                "SELECT description, quantity, unit_price, amount FROM invoice_lines WHERE invoice_id = ? ORDER BY id",
                (resultSet, rowNum) -> new InvoiceLineResponse(
                        resultSet.getString("description"), resultSet.getBigDecimal("quantity"),
                        resultSet.getBigDecimal("unit_price"), resultSet.getBigDecimal("amount")), row.id());
        return new InvoiceResponse(row.id(), row.tenantId(), row.invoiceNumber(), row.period(), row.totalAmount(),
                row.currency(), row.status(), row.createdAt(), lines);
    }

    private void validatePeriod(String period) {
        try {
            YearMonth.parse(period);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must use YYYY-MM format");
        }
    }

    private record InvoiceRow(
            UUID id, UUID tenantId, String invoiceNumber, String period, BigDecimal totalAmount,
            String currency, String status, Instant createdAt) {
    }
}