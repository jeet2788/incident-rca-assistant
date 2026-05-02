package com.rca.embedding;

import com.rca.kafka.AlertEvent;
import com.rca.model.Alert;
import com.rca.model.IncidentEmbedding;
import com.rca.repository.IncidentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Responsible for:
 *   1. Flattening an AlertEvent into a meaningful natural-language string
 *   2. Calling OpenAI to produce a 1536-dim embedding
 *   3. Persisting the embedding to pgvector for future RAG retrieval
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final OpenAiClient openAiClient;
    private final IncidentEmbeddingRepository embeddingRepository;

    /**
     * Generates and stores an embedding for the given alert.
     * Returns the embedding vector so callers can use it immediately for similarity search.
     */
    public float[] embedAndStore(Alert alert, AlertEvent event) {
        String alertText = buildAlertText(event);
        log.debug("Generating embedding for alert: {}", alert.getId());

        float[] vector = openAiClient.embed(alertText);

        IncidentEmbedding embedding = IncidentEmbedding.builder()
            .alert(alert)
            .alertText(alertText)
            .embedding(vector)
            .service(alert.getService())
            .severity(alert.getSeverity())
            .build();

        embeddingRepository.save(embedding);
        log.info("Embedding stored for alert id={} service={}", alert.getId(), alert.getService());

        return vector;
    }

    /**
     * Generates an embedding for a plain text string (e.g. runbook content).
     * Used when indexing runbooks into the vector store.
     */
    public float[] embed(String text) {
        return openAiClient.embed(text);
    }

    /**
     * Converts structured alert fields into a natural-language sentence.
     * LLMs and embedding models perform better on prose than raw JSON keys.
     *
     * Example output:
     * "Service order-service is experiencing High P99 Latency.
     *  Metric p99_latency_ms breached threshold of 2000ms, current value is 4800ms.
     *  Severity: CRITICAL. Environment: production."
     */
    public String buildAlertText(AlertEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Service %s is experiencing %s. ",
            event.getService(), event.getAlertName()));

        if (event.getMetricName() != null && event.getThreshold() != null) {
            sb.append(String.format("Metric %s breached threshold of %s, current value is %s. ",
                event.getMetricName(), event.getThreshold(), event.getCurrentValue()));
        }

        sb.append(String.format("Severity: %s. ", event.getSeverity()));

        if (event.getEnvironment() != null) {
            sb.append(String.format("Environment: %s.", event.getEnvironment()));
        }

        return sb.toString().trim();
    }
}
