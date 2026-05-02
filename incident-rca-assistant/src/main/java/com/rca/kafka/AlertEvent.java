package com.rca.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the alert payload arriving on the alerts.raw Kafka topic.
 * Grafana / CloudWatch webhook payloads are normalised into this shape
 * before publishing to Kafka.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEvent {

    private String alertName;
    private String service;
    private String metricName;
    private String threshold;
    private String currentValue;
    private String severity;          // CRITICAL | HIGH | MEDIUM | LOW
    private String environment;       // production | staging
    private String timestamp;
    private String rawPayload;        // original JSON string kept for audit
}
