package com.saas.device.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.common.events.SessionEndedEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public OutboxPublisher(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            Tracer tracer,
            Propagator propagator,
            TransactionTemplate transactionTemplate,
            @Value("${device.outbox.batch-size}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.propagator = propagator;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${device.outbox.poll-interval-ms}")
    public void publishPending() {
        try {
            transactionTemplate.executeWithoutResult(status -> publishBatch());
        } catch (Exception exception) {
            logger.error("Failed to publish device outbox batch", exception);
        }
    }

    private void publishBatch() {
        List<OutboxRow> events = jdbcTemplate.query(
                "SELECT id, topic, event_key, payload::text AS payload, traceparent FROM outbox_events "
                        + "WHERE status = 'PENDING' ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED",
                (resultSet, row) -> new OutboxRow(
                        resultSet.getObject("id", UUID.class), resultSet.getString("topic"),
                    resultSet.getString("event_key"), resultSet.getString("payload"),
                    resultSet.getString("traceparent")),
                batchSize);
        for (OutboxRow row : events) {
            try {
                SessionEndedEvent event = objectMapper.readValue(row.payload(), SessionEndedEvent.class);
                Span.Builder spanBuilder = row.traceparent() == null
                        ? tracer.spanBuilder().setNoParent()
                        : propagator.extract(Map.of("traceparent", row.traceparent()), Map::get);
                Span publishSpan = spanBuilder
                        .name("publish " + row.topic())
                        .kind(Span.Kind.PRODUCER)
                        .tag("messaging.system", "kafka")
                        .tag("messaging.destination.name", row.topic())
                        .tag("messaging.message.id", event.eventId().toString())
                        .start();
                try (Tracer.SpanInScope ignored = tracer.withSpan(publishSpan)) {
                    kafkaTemplate.send(row.topic(), row.eventKey(), event).get(10, TimeUnit.SECONDS);
                    jdbcTemplate.update(
                        "UPDATE outbox_events SET status = 'SENT', sent_at = now() WHERE id = ?", row.id());
                    logger.info("Outbox event published eventId={} topic={} key={}",
                            event.eventId(), row.topic(), row.eventKey());
                } catch (Exception exception) {
                    publishSpan.error(exception);
                    throw exception;
                } finally {
                    publishSpan.end();
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Outbox event " + row.id() + " was not sent", exception);
            }
        }
    }

    private record OutboxRow(UUID id, String topic, String eventKey, String payload, String traceparent) {
    }
}