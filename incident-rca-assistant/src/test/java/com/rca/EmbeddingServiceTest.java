package com.rca;

import com.rca.embedding.EmbeddingService;
import com.rca.kafka.AlertEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {

    /**
     * Unit test for buildAlertText — no Spring context needed.
     */
    @Test
    void buildAlertText_includesAllFields() {
        EmbeddingService service = new EmbeddingService(null, null);

        AlertEvent event = new AlertEvent();
        event.setService("order-service");
        event.setAlertName("High P99 Latency");
        event.setMetricName("p99_latency_ms");
        event.setThreshold("2000ms");
        event.setCurrentValue("4800ms");
        event.setSeverity("CRITICAL");
        event.setEnvironment("production");

        String text = service.buildAlertText(event);

        assertThat(text).contains("order-service");
        assertThat(text).contains("High P99 Latency");
        assertThat(text).contains("p99_latency_ms");
        assertThat(text).contains("2000ms");
        assertThat(text).contains("4800ms");
        assertThat(text).contains("CRITICAL");
        assertThat(text).contains("production");
    }

    @Test
    void buildAlertText_handlesNullOptionalFields() {
        EmbeddingService service = new EmbeddingService(null, null);

        AlertEvent event = new AlertEvent();
        event.setService("payment-service");
        event.setAlertName("Service Down");
        event.setSeverity("HIGH");

        String text = service.buildAlertText(event);

        assertThat(text).isNotBlank();
        assertThat(text).contains("payment-service");
        assertThat(text).contains("HIGH");
    }
}
