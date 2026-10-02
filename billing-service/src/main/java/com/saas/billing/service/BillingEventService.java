package com.saas.billing.service;

import com.saas.common.events.TenantRegisteredEvent;
import com.saas.common.events.UsageAggregatedEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingEventService {
    private static final Logger logger = LoggerFactory.getLogger(BillingEventService.class);
    private final JdbcTemplate jdbcTemplate;

    public BillingEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void onTenantRegistered(TenantRegisteredEvent event) {
        if (insertProcessedEvent(event.eventId()) == 0) {
            logger.debug("Duplicate tenant event skipped eventId={} tenantId={}", event.eventId(), event.tenantId());
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO tenant_plans (tenant_id, tenant_name, billing_email, plan_type) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id) DO UPDATE SET tenant_name = EXCLUDED.tenant_name, "
                        + "billing_email = EXCLUDED.billing_email, plan_type = EXCLUDED.plan_type",
                event.tenantId(), event.tenantName(), event.adminEmail(), event.planType().name());
            logger.info("Tenant plan initialized eventId={} tenantId={} plan={}",
                event.eventId(), event.tenantId(), event.planType());
    }

    @Transactional
    public void onUsageAggregated(UsageAggregatedEvent event) {
        if (insertProcessedEvent(event.eventId()) == 0) {
            logger.debug("Duplicate usage aggregate skipped eventId={} tenantId={}", event.eventId(), event.tenantId());
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO usage_monthly_snapshot (tenant_id, period, total_seconds) VALUES (?, ?, ?) "
                        + "ON CONFLICT (tenant_id, period) DO UPDATE SET "
                        + "total_seconds = EXCLUDED.total_seconds, updated_at = now()",
                event.tenantId(), event.period(), event.totalSeconds());
            logger.info("Billing usage snapshot updated eventId={} tenantId={} period={} totalSeconds={}",
                event.eventId(), event.tenantId(), event.period(), event.totalSeconds());
    }

    private int insertProcessedEvent(UUID eventId) {
        return jdbcTemplate.update("INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId);
    }
}