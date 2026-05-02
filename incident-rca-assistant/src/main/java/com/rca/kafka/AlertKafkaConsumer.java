package com.rca.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rca.service.RcaPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final RcaPipelineService rcaPipelineService;

    /**
     * Listens on alerts.raw topic.
     * Deserialises the payload, validates severity, then hands off to the RCA pipeline.
     * Only CRITICAL and HIGH alerts trigger automatic RCA to avoid noise.
     */
    @KafkaListener(
        topics = "${rca.kafka.alert-topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"   // 3 concurrent consumers for parallel processing
    )
    public void consume(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.debug("Received alert from topic={} partition={} offset={}", topic, partition, offset);

        try {
            AlertEvent event = objectMapper.readValue(message, AlertEvent.class);

            if (!shouldProcess(event)) {
                log.info("Skipping alert '{}' for service '{}' — severity {} below threshold",
                    event.getAlertName(), event.getService(), event.getSeverity());
                return;
            }

            log.info("Processing CRITICAL/HIGH alert: service={} alert={}", event.getService(), event.getAlertName());
            rcaPipelineService.process(event);

        } catch (Exception e) {
            log.error("Failed to process alert message at offset={}: {}", offset, e.getMessage(), e);
            // Message goes to DLQ via Spring Kafka's DefaultErrorHandler
            throw new RuntimeException("Alert processing failed", e);
        }
    }

    private boolean shouldProcess(AlertEvent event) {
        return event.getSeverity() != null &&
            (event.getSeverity().equalsIgnoreCase("CRITICAL") ||
             event.getSeverity().equalsIgnoreCase("HIGH"));
    }
}
