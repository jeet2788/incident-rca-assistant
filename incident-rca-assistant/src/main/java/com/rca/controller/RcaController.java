package com.rca.controller;

import com.rca.model.RcaReport;
import com.rca.repository.AlertRepository;
import com.rca.repository.RcaReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for querying and managing RCA reports.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rca")
@RequiredArgsConstructor
public class RcaController {

    private final RcaReportRepository rcaReportRepository;
    private final AlertRepository alertRepository;

    @GetMapping
    public List<RcaReport> listReports(
        @RequestParam(required = false) String status
    ) {
        if (status != null) {
            return rcaReportRepository.findByStatus(RcaReport.RcaStatus.valueOf(status.toUpperCase()));
        }
        return rcaReportRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RcaReport> getReport(@PathVariable UUID id) {
        return rcaReportRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/feedback")
    public ResponseEntity<RcaReport> submitFeedback(
        @PathVariable UUID id,
        @RequestBody FeedbackRequest feedback
    ) {
        return rcaReportRepository.findById(id)
            .map(report -> {
                report.setEngineerFeedback(feedback.getComment());
                report.setFeedbackScore(feedback.getScore());
                report.setStatus(RcaReport.RcaStatus.PUBLISHED);
                return ResponseEntity.ok(rcaReportRepository.save(report));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<RcaReport> closeReport(@PathVariable UUID id) {
        return rcaReportRepository.findById(id)
            .map(report -> {
                report.setStatus(RcaReport.RcaStatus.CLOSED);
                return ResponseEntity.ok(rcaReportRepository.save(report));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Inner DTOs ─────────────────────────────────────────────────────────────

    @lombok.Data
    static class FeedbackRequest {
        private String comment;
        private Integer score;  // 1-5
    }
}
