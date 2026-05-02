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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Core orchestration service for the RCA pipeline.
 *
 * Flow:
 *   1. Persist the incoming alert
 *   2. Generate + store an embedding for the alert
 *   3. Retrieve similar past incidents and runbooks via RAG
 *   4. Build a prompt and call the LLM to generate the RCA
 *   5. Persist the RCA report
 *   6. Publish the result to Kafka and notify Slack
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RcaPipelineService {

    private final AlertRepository alertRepository;
    private final RcaReportRepository rcaReportRepository;
    private final EmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;
    private final RcaGenerationService rcaGenerationService;
    private final SlackNotificationService slackNotificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(AlertEvent event) {
        log.info("Starting RCA pipeline for alert: {} service: {}", event.getAlertName(), event.getService());

        // Step 1 – Persist the alert
        Alert alert = persistAlert(event);

        try {
            // Step 2 – Generate and store embedding
            float[] embedding = embeddingService.embedAndStore(alert, event);

            // Step 3 – RAG retrieval
            RetrievalResult retrieval = ragRetrievalService.retrieve(embedding, event.getService());
            log.debug("RAG context length: {} chars", retrieval.getContext().length());

            // Step 4 – Generate RCA via LLM
            RcaReport report = rcaGenerationService.generate(alert, event, retrieval);

            // Step 5 – Persist report
            rcaReportRepository.save(report);
            log.info("RCA report saved id={} for alert id={}", report.getId(), alert.getId());

            // Step 6 – Notify Slack
            slackNotificationService.postRca(report, alert);

            // Mark alert as RESOLVED
            alert.setStatus(Alert.AlertStatus.RESOLVED);
            alertRepository.save(alert);

        } catch (Exception e) {
            log.error("RCA pipeline failed for alert id={}: {}", alert.getId(), e.getMessage(), e);
            alert.setStatus(Alert.AlertStatus.PENDING); // reset for retry
            alertRepository.save(alert);
            throw e;
        }
    }

    private Alert persistAlert(AlertEvent event) {
        String alertText = embeddingService.buildAlertText(event);

        Alert alert = Alert.builder()
            .alertName(event.getAlertName())
            .service(event.getService())
            .metricName(event.getMetricName())
            .threshold(event.getThreshold())
            .currentValue(event.getCurrentValue())
            .severity(event.getSeverity())
            .environment(event.getEnvironment() != null ? event.getEnvironment() : "production")
            .alertText(alertText)
            .rawPayload(event.getRawPayload())
            .status(Alert.AlertStatus.PROCESSING)
            .build();

        Alert saved = alertRepository.save(alert);
        log.debug("Alert persisted with id={}", saved.getId());
        return saved;
    }
}
