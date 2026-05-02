package com.rca.service;

import com.rca.embedding.EmbeddingService;
import com.rca.model.Runbook;
import com.rca.repository.RunbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles ingestion of runbooks into the vector store.
 * Call ingest() to index a new or updated runbook for RAG retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunbookIngestionService {

    private final RunbookRepository runbookRepository;
    private final EmbeddingService embeddingService;

    @Transactional
    public Runbook ingest(String title, String service, String content, String[] tags) {
        log.info("Ingesting runbook: '{}' for service '{}'", title, service);

        float[] embedding = embeddingService.embed(content);

        Runbook runbook = Runbook.builder()
            .title(title)
            .service(service)
            .content(content)
            .embedding(embedding)
            .tags(tags)
            .build();

        Runbook saved = runbookRepository.save(runbook);
        log.info("Runbook ingested with id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Runbook reEmbed(java.util.UUID runbookId) {
        Runbook runbook = runbookRepository.findById(runbookId)
            .orElseThrow(() -> new IllegalArgumentException("Runbook not found: " + runbookId));

        float[] embedding = embeddingService.embed(runbook.getContent());
        runbook.setEmbedding(embedding);

        Runbook saved = runbookRepository.save(runbook);
        log.info("Re-embedded runbook id={}", runbookId);
        return saved;
    }
}
