package com.rca.rag;

import com.rca.model.IncidentEmbedding;
import com.rca.model.Runbook;
import com.rca.repository.IncidentEmbeddingRepository;
import com.rca.repository.RunbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) service.
 *
 * Given a query embedding (from the incoming alert), retrieves:
 *   - Top-K similar past incidents (with their RCA summaries if available)
 *   - Top-K relevant runbook sections
 *
 * The retrieved context is assembled into a single string that gets
 * injected into the LLM prompt alongside the current alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final IncidentEmbeddingRepository incidentEmbeddingRepository;
    private final RunbookRepository runbookRepository;

    @Value("${rca.rag.top-k}")
    private int topK;

    @Value("${rca.rag.similarity-threshold}")
    private double similarityThreshold;

    /**
     * Main retrieval entry point.
     * Returns assembled context string ready to be injected into the LLM prompt.
     */
    public RetrievalResult retrieve(float[] queryEmbedding, String service) {
        log.debug("RAG retrieval for service={} topK={}", service, topK);

        List<IncidentEmbedding> similarIncidents = findSimilarIncidents(queryEmbedding, service);
        List<Runbook> relevantRunbooks = findRelevantRunbooks(queryEmbedding, service);

        String context = buildContext(similarIncidents, relevantRunbooks);

        log.info("RAG retrieved {} past incidents and {} runbook sections for service={}",
            similarIncidents.size(), relevantRunbooks.size(), service);

        return RetrievalResult.builder()
            .context(context)
            .incidentIds(similarIncidents.stream().map(i -> i.getId().toString()).toList())
            .runbookIds(relevantRunbooks.stream().map(r -> r.getId().toString()).toList())
            .build();
    }

    private List<IncidentEmbedding> findSimilarIncidents(float[] queryEmbedding, String service) {
        String vectorStr = toVectorString(queryEmbedding);
        // Service-scoped search first; fallback to cross-service if insufficient results
        List<IncidentEmbedding> results = incidentEmbeddingRepository
            .findSimilarByService(vectorStr, service, topK);

        if (results.size() < 2) {
            log.debug("Few service-scoped results ({}), expanding to cross-service search", results.size());
            results = incidentEmbeddingRepository.findSimilar(vectorStr, topK);
        }
        return results;
    }

    private List<Runbook> findRelevantRunbooks(float[] queryEmbedding, String service) {
        String vectorStr = toVectorString(queryEmbedding);
        List<Runbook> results = runbookRepository.findSimilarByService(vectorStr, service, topK);
        if (results.size() < 2) {
            results = runbookRepository.findSimilar(vectorStr, topK);
        }
        return results;
    }

    /**
     * Assembles retrieved incidents and runbooks into a readable context block
     * that will be included in the LLM prompt.
     */
    private String buildContext(List<IncidentEmbedding> incidents, List<Runbook> runbooks) {
        StringBuilder ctx = new StringBuilder();

        if (!incidents.isEmpty()) {
            ctx.append("=== SIMILAR PAST INCIDENTS ===\n");
            for (int i = 0; i < incidents.size(); i++) {
                IncidentEmbedding inc = incidents.get(i);
                ctx.append(String.format("\n[Incident %d]\n", i + 1));
                ctx.append("Alert: ").append(inc.getAlertText()).append("\n");
                if (inc.getRcaSummary() != null) {
                    ctx.append("Resolution: ").append(inc.getRcaSummary()).append("\n");
                }
            }
        }

        if (!runbooks.isEmpty()) {
            ctx.append("\n=== RELEVANT RUNBOOKS ===\n");
            for (int i = 0; i < runbooks.size(); i++) {
                Runbook rb = runbooks.get(i);
                ctx.append(String.format("\n[Runbook %d: %s]\n", i + 1, rb.getTitle()));
                // Trim runbook content to avoid blowing the context window
                String content = rb.getContent().length() > 800
                    ? rb.getContent().substring(0, 800) + "..."
                    : rb.getContent();
                ctx.append(content).append("\n");
            }
        }

        return ctx.toString();
    }

    /**
     * Converts float[] to pgvector string format: '[0.1,0.2,...]'
     */
    public static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
