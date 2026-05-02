package com.rca.controller;

import com.rca.model.RcaReport;
import com.rca.repository.AlertRepository;
import com.rca.repository.RcaReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RcaController.
 * Tests all REST endpoints for querying and managing RCA reports.
 */
@ExtendWith(MockitoExtension.class)
public class RcaControllerTest {

    @Mock
    private RcaReportRepository rcaReportRepository;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private RcaController rcaController;

    private RcaReport testReport;
    private UUID testReportId;

    @BeforeEach
    void setUp() {
        testReportId = UUID.randomUUID();
        testReport = createTestRcaReport(testReportId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET /api/v1/rca - List all reports or filter by status
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testListReports_NoFilter() {
        // Arrange
        List<RcaReport> reports = List.of(
            testReport,
            createTestRcaReport(UUID.randomUUID())
        );
        when(rcaReportRepository.findAll()).thenReturn(reports);

        // Act
        List<RcaReport> result = rcaController.listReports(null);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(reports);
        verify(rcaReportRepository).findAll();
        verify(rcaReportRepository, never()).findByStatus(any());
    }

    @Test
    void testListReports_FilterByDraftStatus() {
        // Arrange
        List<RcaReport> draftReports = List.of(testReport);
        when(rcaReportRepository.findByStatus(RcaReport.RcaStatus.DRAFT)).thenReturn(draftReports);

        // Act
        List<RcaReport> result = rcaController.listReports("DRAFT");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(RcaReport.RcaStatus.DRAFT);
        verify(rcaReportRepository).findByStatus(RcaReport.RcaStatus.DRAFT);
    }

    @Test
    void testListReports_FilterByPublishedStatus() {
        // Arrange
        RcaReport publishedReport = createTestRcaReport(UUID.randomUUID());
        publishedReport.setStatus(RcaReport.RcaStatus.PUBLISHED);
        List<RcaReport> publishedReports = List.of(publishedReport);
        when(rcaReportRepository.findByStatus(RcaReport.RcaStatus.PUBLISHED)).thenReturn(publishedReports);

        // Act
        List<RcaReport> result = rcaController.listReports("published"); // lowercase to test case-insensitivity

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(RcaReport.RcaStatus.PUBLISHED);
        verify(rcaReportRepository).findByStatus(RcaReport.RcaStatus.PUBLISHED);
    }

    @Test
    void testListReports_EmptyList() {
        // Arrange
        when(rcaReportRepository.findAll()).thenReturn(List.of());

        // Act
        List<RcaReport> result = rcaController.listReports(null);

        // Assert
        assertThat(result).isEmpty();
        verify(rcaReportRepository).findAll();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET /api/v1/rca/{id} - Get specific report by ID
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testGetReport_Found() {
        // Arrange
        when(rcaReportRepository.findById(testReportId)).thenReturn(Optional.of(testReport));

        // Act
        ResponseEntity<RcaReport> response = rcaController.getReport(testReportId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(testReport);
        verify(rcaReportRepository).findById(testReportId);
    }

    @Test
    void testGetReport_NotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(rcaReportRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<RcaReport> response = rcaController.getReport(nonExistentId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        verify(rcaReportRepository).findById(nonExistentId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/rca/{id}/feedback - Submit engineer feedback
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testSubmitFeedback_Success() {
        // Arrange
        RcaController.FeedbackRequest feedback = new RcaController.FeedbackRequest();
        feedback.setComment("Great analysis, very helpful!");
        feedback.setScore(5);

        RcaReport reportAfterFeedback = createTestRcaReport(testReportId);
        reportAfterFeedback.setEngineerFeedback(feedback.getComment());
        reportAfterFeedback.setFeedbackScore(feedback.getScore());
        reportAfterFeedback.setStatus(RcaReport.RcaStatus.PUBLISHED);

        when(rcaReportRepository.findById(testReportId)).thenReturn(Optional.of(testReport));
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(reportAfterFeedback);

        // Act
        ResponseEntity<RcaReport> response = rcaController.submitFeedback(testReportId, feedback);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEngineerFeedback()).isEqualTo(feedback.getComment());
        assertThat(response.getBody().getFeedbackScore()).isEqualTo(feedback.getScore());
        assertThat(response.getBody().getStatus()).isEqualTo(RcaReport.RcaStatus.PUBLISHED);
        verify(rcaReportRepository).findById(testReportId);
        verify(rcaReportRepository).save(any(RcaReport.class));
    }

    @Test
    void testSubmitFeedback_ReportNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        RcaController.FeedbackRequest feedback = new RcaController.FeedbackRequest();
        feedback.setComment("Test feedback");
        feedback.setScore(4);

        when(rcaReportRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<RcaReport> response = rcaController.submitFeedback(nonExistentId, feedback);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        verify(rcaReportRepository).findById(nonExistentId);
        verify(rcaReportRepository, never()).save(any());
    }

    @Test
    void testSubmitFeedback_WithLowScore() {
        // Arrange
        RcaController.FeedbackRequest feedback = new RcaController.FeedbackRequest();
        feedback.setComment("Analysis was incomplete");
        feedback.setScore(1);

        RcaReport reportAfterFeedback = createTestRcaReport(testReportId);
        reportAfterFeedback.setEngineerFeedback(feedback.getComment());
        reportAfterFeedback.setFeedbackScore(feedback.getScore());
        reportAfterFeedback.setStatus(RcaReport.RcaStatus.PUBLISHED);

        when(rcaReportRepository.findById(testReportId)).thenReturn(Optional.of(testReport));
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(reportAfterFeedback);

        // Act
        ResponseEntity<RcaReport> response = rcaController.submitFeedback(testReportId, feedback);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getFeedbackScore()).isEqualTo(1);
        verify(rcaReportRepository).save(any(RcaReport.class));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/rca/{id}/close - Close RCA report
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testCloseReport_Success() {
        // Arrange
        RcaReport closedReport = createTestRcaReport(testReportId);
        closedReport.setStatus(RcaReport.RcaStatus.CLOSED);

        when(rcaReportRepository.findById(testReportId)).thenReturn(Optional.of(testReport));
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(closedReport);

        // Act
        ResponseEntity<RcaReport> response = rcaController.closeReport(testReportId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(RcaReport.RcaStatus.CLOSED);
        verify(rcaReportRepository).findById(testReportId);
        verify(rcaReportRepository).save(any(RcaReport.class));
    }

    @Test
    void testCloseReport_NotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(rcaReportRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<RcaReport> response = rcaController.closeReport(nonExistentId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        verify(rcaReportRepository).findById(nonExistentId);
        verify(rcaReportRepository, never()).save(any());
    }

    @Test
    void testCloseReport_AlreadyClosed() {
        // Arrange
        RcaReport alreadyClosedReport = createTestRcaReport(testReportId);
        alreadyClosedReport.setStatus(RcaReport.RcaStatus.CLOSED);

        when(rcaReportRepository.findById(testReportId)).thenReturn(Optional.of(alreadyClosedReport));
        when(rcaReportRepository.save(any(RcaReport.class))).thenReturn(alreadyClosedReport);

        // Act
        ResponseEntity<RcaReport> response = rcaController.closeReport(testReportId);

        // Assert - Should successfully return even if already closed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(RcaReport.RcaStatus.CLOSED);
        verify(rcaReportRepository).save(any(RcaReport.class));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────────

    private RcaReport createTestRcaReport(UUID id) {
        return RcaReport.builder()
            .id(id)
            .rootCause("Database connection pool exhaustion")
            .impact("Payment processing delays")
            .timeline("10:25 - Issue started\n10:30 - Alert triggered")
            .fixApplied("Increased connection pool size")
            .prevention("Implement monitoring")
            .fullReport("Complete report text")
            .modelUsed("gpt-4")
            .tokensUsed(1500)
            .sourcesUsed("{\"incidents\": [\"inc-1\"], \"runbooks\": [\"rb-1\"]}")
            .status(RcaReport.RcaStatus.DRAFT)
            .slackTs("1234567890.123456")
            .build();
    }
}

