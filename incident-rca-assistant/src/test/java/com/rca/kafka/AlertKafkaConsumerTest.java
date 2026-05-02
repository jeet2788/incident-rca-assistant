package com.rca.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rca.service.RcaPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertKafkaConsumer.
 * Tests Kafka message consumption, deserialization, and severity filtering.
 */
@ExtendWith(MockitoExtension.class)
public class AlertKafkaConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RcaPipelineService rcaPipelineService;

    @InjectMocks
    private AlertKafkaConsumer alertKafkaConsumer;

    private AlertEvent testAlertEvent;
    private String testAlertJson;

    @BeforeEach
    void setUp() {
        testAlertEvent = createTestAlertEvent();
        testAlertJson = "{\"alertName\": \"High Latency\", \"service\": \"payment-service\", \"severity\": \"CRITICAL\"}";
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Message Consumption and Deserialization
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testConsume_CriticalAlert_Success() throws Exception {
        // Arrange
        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(testAlertEvent);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(objectMapper).readValue(testAlertJson, AlertEvent.class);
        verify(rcaPipelineService).process(testAlertEvent);
    }

    @Test
    void testConsume_HighAlert_Success() throws Exception {
        // Arrange
        AlertEvent highAlert = createTestAlertEvent();
        highAlert.setSeverity("HIGH");

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(highAlert);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(objectMapper).readValue(testAlertJson, AlertEvent.class);
        verify(rcaPipelineService).process(highAlert);
    }

    @Test
    void testConsume_MediumAlert_Skipped() throws Exception {
        // Arrange
        AlertEvent mediumAlert = createTestAlertEvent();
        mediumAlert.setSeverity("MEDIUM");

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(mediumAlert);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(objectMapper).readValue(testAlertJson, AlertEvent.class);
        verify(rcaPipelineService, never()).process(any());
    }

    @Test
    void testConsume_LowAlert_Skipped() throws Exception {
        // Arrange
        AlertEvent lowAlert = createTestAlertEvent();
        lowAlert.setSeverity("LOW");

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(lowAlert);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(rcaPipelineService, never()).process(any());
    }

    @Test
    void testConsume_NullSeverity_Skipped() throws Exception {
        // Arrange
        AlertEvent alertWithNullSeverity = createTestAlertEvent();
        alertWithNullSeverity.setSeverity(null);

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(alertWithNullSeverity);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(rcaPipelineService, never()).process(any());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Severity Case-Insensitivity
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testConsume_LowercaseCritical_Success() throws Exception {
        // Arrange
        AlertEvent lowercaseAlert = createTestAlertEvent();
        lowercaseAlert.setSeverity("critical");

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(lowercaseAlert);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(rcaPipelineService).process(lowercaseAlert);
    }

    @Test
    void testConsume_MixedCaseHigh_Success() throws Exception {
        // Arrange
        AlertEvent mixedCaseAlert = createTestAlertEvent();
        mixedCaseAlert.setSeverity("High");

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(mixedCaseAlert);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert
        verify(rcaPipelineService).process(mixedCaseAlert);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Error Handling
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testConsume_DeserializationError() throws Exception {
        // Arrange
        when(objectMapper.readValue(testAlertJson, AlertEvent.class))
            .thenThrow(new Exception("JSON parsing failed"));

        // Act & Assert
        assertThatThrownBy(() -> alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Alert processing failed");

        verify(rcaPipelineService, never()).process(any());
    }

    @Test
    void testConsume_PipelineProcessingError() throws Exception {
        // Arrange
        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(testAlertEvent);
        doThrow(new RuntimeException("RCA pipeline failed"))
            .when(rcaPipelineService).process(testAlertEvent);

        // Act & Assert
        assertThatThrownBy(() -> alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Alert processing failed");

        verify(rcaPipelineService).process(testAlertEvent);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Kafka Metadata
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testConsume_WithKafkaMetadata() throws Exception {
        // Arrange
        String topic = "alerts.raw";
        int partition = 3;
        long offset = 12345L;

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(testAlertEvent);

        // Act
        alertKafkaConsumer.consume(testAlertJson, topic, partition, offset);

        // Assert - Verify metadata is handled correctly
        verify(objectMapper).readValue(testAlertJson, AlertEvent.class);
        verify(rcaPipelineService).process(testAlertEvent);
    }

    @Test
    void testConsume_DifferentPartitions() throws Exception {
        // Arrange
        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(testAlertEvent);

        // Act - Process from different partitions
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 1, 101L);
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 2, 102L);

        // Assert - All should be processed
        verify(rcaPipelineService, times(3)).process(testAlertEvent);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Multiple Message Processing
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testConsume_MultipleAlerts() throws Exception {
        // Arrange
        AlertEvent alert1 = createTestAlertEvent();
        alert1.setAlertName("Alert 1");
        alert1.setSeverity("CRITICAL");

        AlertEvent alert2 = createTestAlertEvent();
        alert2.setAlertName("Alert 2");
        alert2.setSeverity("HIGH");

        AlertEvent alert3 = createTestAlertEvent();
        alert3.setAlertName("Alert 3");
        alert3.setSeverity("MEDIUM"); // Should be skipped

        String json1 = "{\"alertName\": \"Alert 1\"}";
        String json2 = "{\"alertName\": \"Alert 2\"}";
        String json3 = "{\"alertName\": \"Alert 3\"}";

        when(objectMapper.readValue(json1, AlertEvent.class)).thenReturn(alert1);
        when(objectMapper.readValue(json2, AlertEvent.class)).thenReturn(alert2);
        when(objectMapper.readValue(json3, AlertEvent.class)).thenReturn(alert3);

        // Act
        alertKafkaConsumer.consume(json1, "alerts.raw", 0, 100L);
        alertKafkaConsumer.consume(json2, "alerts.raw", 0, 101L);
        alertKafkaConsumer.consume(json3, "alerts.raw", 0, 102L);

        // Assert - Only CRITICAL and HIGH severity alerts should be processed
        verify(rcaPipelineService).process(alert1);
        verify(rcaPipelineService).process(alert2);
        verify(rcaPipelineService, never()).process(alert3);
        verify(rcaPipelineService, times(2)).process(any());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Integration with RcaPipelineService
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testConsume_PassesCompleteAlertEvent() throws Exception {
        // Arrange
        AlertEvent fullAlert = new AlertEvent();
        fullAlert.setAlertName("Database Down");
        fullAlert.setService("auth-service");
        fullAlert.setMetricName("health_check");
        fullAlert.setThreshold("should_be_up");
        fullAlert.setCurrentValue("is_down");
        fullAlert.setSeverity("CRITICAL");
        fullAlert.setEnvironment("production");
        fullAlert.setTimestamp("2026-05-02T10:30:00Z");
        fullAlert.setRawPayload("{\"status\": \"down\"}");

        when(objectMapper.readValue(testAlertJson, AlertEvent.class)).thenReturn(fullAlert);

        // Act
        alertKafkaConsumer.consume(testAlertJson, "alerts.raw", 0, 100L);

        // Assert - Verify complete alert is passed to pipeline
        ArgumentCaptor<AlertEvent> alertCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(rcaPipelineService).process(alertCaptor.capture());

        AlertEvent capturedAlert = alertCaptor.getValue();
        assertThat(capturedAlert.getAlertName()).isEqualTo("Database Down");
        assertThat(capturedAlert.getService()).isEqualTo("auth-service");
        assertThat(capturedAlert.getSeverity()).isEqualTo("CRITICAL");
        assertThat(capturedAlert.getEnvironment()).isEqualTo("production");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────────

    private AlertEvent createTestAlertEvent() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("High Latency");
        event.setService("payment-service");
        event.setMetricName("p99_latency_ms");
        event.setThreshold("2000ms");
        event.setCurrentValue("4800ms");
        event.setSeverity("CRITICAL");
        event.setEnvironment("production");
        event.setTimestamp("2026-05-02T10:30:00Z");
        event.setRawPayload("{\"alert\": \"high_latency\"}");
        return event;
    }
}

