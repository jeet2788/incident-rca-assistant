package com.rca.controller;

import com.rca.model.Runbook;
import com.rca.service.RunbookIngestionService;
import com.rca.repository.RunbookRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for managing and ingesting runbooks into the vector knowledge base.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/runbooks")
@RequiredArgsConstructor
public class RunbookController {

    private final RunbookRepository runbookRepository;
    private final RunbookIngestionService runbookIngestionService;

    @GetMapping
    public List<Runbook> listRunbooks(@RequestParam(required = false) String service) {
        if (service != null) {
            return runbookRepository.findByService(service);
        }
        return runbookRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Runbook> getRunbook(@PathVariable UUID id) {
        return runbookRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Runbook> ingestRunbook(@Valid @RequestBody RunbookRequest request) {
        Runbook saved = runbookIngestionService.ingest(
            request.getTitle(),
            request.getService(),
            request.getContent(),
            request.getTags()
        );
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/re-embed")
    public ResponseEntity<Runbook> reEmbed(@PathVariable UUID id) {
        Runbook updated = runbookIngestionService.reEmbed(id);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRunbook(@PathVariable UUID id) {
        if (!runbookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        runbookRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Inner DTOs ─────────────────────────────────────────────────────────────

    @Data
    static class RunbookRequest {
        @NotBlank
        private String title;
        private String service;
        @NotBlank
        private String content;
        private String[] tags;
    }
}
