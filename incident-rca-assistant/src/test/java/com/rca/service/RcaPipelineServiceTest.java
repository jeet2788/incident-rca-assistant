package com.rca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rca.embedding.EmbeddingService;
import com.rca.kafka.AlertEvent;
import com.rca.model.Alert;
import com.rca.model.RcaReport;
import com.rca.rag.RagRetrievalService;
import com.rca.rag.RetrievalResult;
import com.rca.repository.AlertRepository;
import com.rca.repository.RcaReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RcaPipelineService.
 * Tests the complete RCA pipeline flow from alert ingestion to report generation and Slack notification.
 */
@ExtendWith(MockitoExtension.class)
public class RcaPipelineServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private RcaReportRepository rcaReportRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private RcaGenerationService rcaGenerationService;

    @Mock
    private SlackNotificationService slackNotificationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RcaPipelineService rcaPipelineService;

    private AlertEvent testAlertEvent;
    private Alert testAlert;
    private float[] testEmbedding;
    private RetrievalResult testRetrievalResult;
    private RcaReport testRcaReport;

    @BeforeEach
    void setUp() {
        // Setup test data
        testAlertEvent = createTestAlertEvent();
        testAlert = createTestAlert();
        testEmbedding = createTestEmbedding();
        testRetrievalResult = createTestRetrievalResult();
        testRcaReport = createTestRcaReport();
    }

    /**
     * Test successful RCA pipeline execution with all steps completing successfully.
     */
    @Test
    void testProcessAlert_Success() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Test alert text");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(testAlert, testAlertEvent, testRetrievalResult)).thenReturn(testRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(testRcaReport);

        // Act
        rcaPipelineService.process(testAlertEvent);

        // Assert - Verify all steps were called in sequence
        verify(embeddingService).buildAlertText(testAlertEvent);
        verify(alertRepository, times(2)).save(any(Alert.class)); // Once in persistAlert, once for RESOLVED status
        verify(embeddingService).embedAndStore(any(Alert.class), eq(testAlertEvent));
        verify(ragRetrievalService).retrieve(testEmbedding, testAlertEvent.getService());
        verify(rcaGenerationService).generate(testAlert, testAlertEvent, testRetrievalResult);
        verify(rcaReportRepository).save(any(RcaReport.class));
        verify(slackNotificationService).postRca(testRcaReport, testAlert);

        // Verify alert status is set to RESOLVED
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(2)).save(alertCaptor.capture());
        Alert finalAlert = alertCaptor.getAllValues().get(1);
        assertThat(finalAlert.getStatus()).isEqualTo(Alert.AlertStatus.RESOLVED);
    }

    /**
     * Test RCA pipeline with exception during embedding generation.
     */
    @Test
    void testProcessAlert_EmbeddingFailure() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Test alert text");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent)))
            .thenThrow(new RuntimeException("Embedding service failed"));

        // Act & Assert
        assertThatThrownBy(() -> rcaPipelineService.process(testAlertEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Embedding service failed");

        // Verify alert status is reset to PENDING on failure
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(2)).save(alertCaptor.capture());
        List<Alert> savedAlerts = alertCaptor.getAllValues();

        assertThat(savedAlerts.get(0).getStatus()).isEqualTo(Alert.AlertStatus.PROCESSING);
        assertThat(savedAlerts.get(1).getStatus()).isEqualTo(Alert.AlertStatus.PENDING);

        // Verify subsequent steps are NOT called
        verify(ragRetrievalService, never()).retrieve(any(), anyString());
        verify(rcaGenerationService, never()).generate(any(), any(), any());
        verify(slackNotificationService, never()).postRca(any(), any());
    }

    /**
     * Test RCA pipeline with exception during RAG retrieval.
     */
    @Test
    void testProcessAlert_RagRetrievalFailure() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Test alert text");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService()))
            .thenThrow(new RuntimeException("RAG service failed"));

        // Act & Assert
        assertThatThrownBy(() -> rcaPipelineService.process(testAlertEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("RAG service failed");

        // Verify alert is reset to PENDING
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(2)).save(alertCaptor.capture());
        Alert failedAlert = alertCaptor.getAllValues().get(1);
        assertThat(failedAlert.getStatus()).isEqualTo(Alert.AlertStatus.PENDING);

        // Verify LLM generation is NOT called
        verify(rcaGenerationService, never()).generate(any(), any(), any());
        verify(slackNotificationService, never()).postRca(any(), any());
    }

    /**
     * Test RCA pipeline with exception during LLM-based RCA generation.
     */
    @Test
    void testProcessAlert_RcaGenerationFailure() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Test alert text");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(testAlert, testAlertEvent, testRetrievalResult))
            .thenThrow(new RuntimeException("LLM service failed"));

        // Act & Assert
        assertThatThrownBy(() -> rcaPipelineService.process(testAlertEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("LLM service failed");

        // Verify alert is reset to PENDING
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(2)).save(alertCaptor.capture());
        Alert failedAlert = alertCaptor.getAllValues().get(1);
        assertThat(failedAlert.getStatus()).isEqualTo(Alert.AlertStatus.PENDING);

        // Verify report is NOT saved and Slack is NOT notified
        verify(rcaReportRepository, never()).save(any());
        verify(slackNotificationService, never()).postRca(any(), any());
    }

    /**
     * Test that Slack notification failure does not prevent pipeline completion.
     * (The exception is caught and logged but pipeline continues)
     */
    @Test
    void testProcessAlert_SlackNotificationFailure() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Test alert text");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(testAlert, testAlertEvent, testRetrievalResult)).thenReturn(testRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(testRcaReport);
        doThrow(new RuntimeException("Slack service failed"))
            .when(slackNotificationService).postRca(testRcaReport, testAlert);

        // Act - Note: SlackNotificationService catches and logs the exception, so this should not throw
        rcaPipelineService.process(testAlertEvent);

        // Assert - Verify all steps were called
        verify(slackNotificationService).postRca(testRcaReport, testAlert);
        verify(rcaReportRepository).save(any(RcaReport.class));

        // Alert should still be marked as RESOLVED even though Slack failed
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(2)).save(alertCaptor.capture());
        Alert finalAlert = alertCaptor.getAllValues().get(1);
        assertThat(finalAlert.getStatus()).isEqualTo(Alert.AlertStatus.RESOLVED);
    }

    /**
     * Test that alert text is correctly built from AlertEvent.
     */
    @Test
    void testProcessAlert_AlertTextConstruction() {
        // Arrange
        String expectedAlertText = "Service payment-service is experiencing High Latency. " +
            "Metric p99_latency_ms breached threshold of 2000ms, current value is 4800ms. " +
            "Severity: CRITICAL. Environment: production.";

        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn(expectedAlertText);
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(testAlert, testAlertEvent, testRetrievalResult)).thenReturn(testRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(testRcaReport);

        // Act
        rcaPipelineService.process(testAlertEvent);

        // Assert
        verify(embeddingService).buildAlertText(testAlertEvent);

        // Verify alert was persisted with correct alert text
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert savedAlert = alertCaptor.getValue();

        assertThat(savedAlert.getAlertName()).isEqualTo(testAlertEvent.getAlertName());
        assertThat(savedAlert.getService()).isEqualTo(testAlertEvent.getService());
        assertThat(savedAlert.getSeverity()).isEqualTo(testAlertEvent.getSeverity());
        assertThat(savedAlert.getEnvironment()).isEqualTo(testAlertEvent.getEnvironment());
    }

    /**
     * Test that default environment is set to "production" if not provided in alert event.
     */
    @Test
    void testProcessAlert_DefaultEnvironment() {
        // Arrange
        AlertEvent eventWithoutEnvironment = new AlertEvent();
        eventWithoutEnvironment.setAlertName("Test Alert");
        eventWithoutEnvironment.setService("test-service");
        eventWithoutEnvironment.setSeverity("CRITICAL");
        eventWithoutEnvironment.setEnvironment(null); // No environment specified

        Alert alertWithDefaultEnv = Alert.builder()
            .id(UUID.randomUUID())
            .alertName(eventWithoutEnvironment.getAlertName())
            .service(eventWithoutEnvironment.getService())
            .severity(eventWithoutEnvironment.getSeverity())
            .environment("production") // Should default to production
            .status(Alert.AlertStatus.PROCESSING)
            .build();

        when(embeddingService.buildAlertText(eventWithoutEnvironment)).thenReturn("Test alert");
        when(alertRepository.save(any(Alert.class))).thenReturn(alertWithDefaultEnv);
        when(embeddingService.embedAndStore(any(Alert.class), eq(eventWithoutEnvironment))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, eventWithoutEnvironment.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(any(), any(), any())).thenReturn(testRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(testRcaReport);

        // Act
        rcaPipelineService.process(eventWithoutEnvironment);

        // Assert - Verify environment defaults to "production"
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert savedAlert = alertCaptor.getValue();

        assertThat(savedAlert.getEnvironment()).isEqualTo("production");
    }

    /**
     * Test that RAG retrieval is called with correct parameters.
     */
    @Test
    void testProcessAlert_RagRetrievalParameters() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Test alert text");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(testAlert, testAlertEvent, testRetrievalResult)).thenReturn(testRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(testRcaReport);

        // Act
        rcaPipelineService.process(testAlertEvent);

        // Assert - Verify RAG is called with the embedding and service name
        verify(ragRetrievalService).retrieve(testEmbedding, testAlertEvent.getService());
    }

    /**
     * Test complete flow with all dependencies working correctly.
     */
    @Test
    void testProcessAlert_CompleteFlow() {
        // Arrange
        when(embeddingService.buildAlertText(testAlertEvent)).thenReturn("Complete flow test");
        when(alertRepository.save(any(Alert.class))).thenReturn(testAlert);
        when(embeddingService.embedAndStore(any(Alert.class), eq(testAlertEvent))).thenReturn(testEmbedding);
        when(ragRetrievalService.retrieve(testEmbedding, testAlertEvent.getService())).thenReturn(testRetrievalResult);
        when(rcaGenerationService.generate(testAlert, testAlertEvent, testRetrievalResult)).thenReturn(testRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(testRcaReport);

        // Act
        rcaPipelineService.process(testAlertEvent);

        // Assert - Verify complete execution order
        InOrder inOrder = inOrder(
            embeddingService, alertRepository, ragRetrievalService,
            rcaGenerationService, rcaReportRepository, slackNotificationService
        );

        inOrder.verify(embeddingService).buildAlertText(testAlertEvent);
        inOrder.verify(alertRepository).save(any(Alert.class)); // Persist alert
        inOrder.verify(embeddingService).embedAndStore(any(Alert.class), eq(testAlertEvent));
        inOrder.verify(ragRetrievalService).retrieve(testEmbedding, testAlertEvent.getService());
        inOrder.verify(rcaGenerationService).generate(testAlert, testAlertEvent, testRetrievalResult);
        inOrder.verify(rcaReportRepository).save(any(RcaReport.class));
        inOrder.verify(slackNotificationService).postRca(testRcaReport, testAlert);
        inOrder.verify(alertRepository).save(any(Alert.class)); // Update alert to RESOLVED
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Methods to Create Test Data
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

    private Alert createTestAlert() {
        return Alert.builder()
            .id(UUID.randomUUID())
            .alertName("High Latency")
            .service("payment-service")
            .metricName("p99_latency_ms")
            .threshold("2000ms")
            .currentValue("4800ms")
            .severity("CRITICAL")
            .environment("production")
            .alertText("Service payment-service is experiencing High Latency")
            .rawPayload("{\"alert\": \"high_latency\"}")
            .status(Alert.AlertStatus.PROCESSING)
            .build();
    }

    private float[] createTestEmbedding() {
        float[] embedding = new float[1536]; // OpenAI embedding dimension
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() - 0.5);
        }
        return embedding;
    }

    private RetrievalResult createTestRetrievalResult() {
        return RetrievalResult.builder()
            .context("Similar incidents and runbooks context")
            .incidentIds(List.of("incident-1", "incident-2"))
            .runbookIds(List.of("runbook-1", "runbook-2"))
            .build();
    }

    private RcaReport createTestRcaReport() {
        return RcaReport.builder()
            .id(UUID.randomUUID())
            .alert(testAlert)
            .rootCause("Database connection pool exhaustion")
            .impact("Payment processing delays affecting 15% of transactions")
            .timeline("10:25 - Database connection pool utilization reached 100%\n10:30 - Alerts triggered")
            .fixApplied("Increased connection pool size from 50 to 100")
            .prevention("Implement connection pool monitoring and auto-scaling")
            .fullReport("Complete RCA report")
            .modelUsed("gpt-4")
            .sourcesUsed("{\"incidents\": [\"incident-1\"], \"runbooks\": [\"runbook-1\"]}")
            .status(RcaReport.RcaStatus.DRAFT)
            .build();
    }
}

