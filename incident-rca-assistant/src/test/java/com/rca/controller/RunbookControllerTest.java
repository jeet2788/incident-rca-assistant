package com.rca.controller;

import com.rca.model.Runbook;
import com.rca.repository.RunbookRepository;
import com.rca.service.RunbookIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RunbookController.
 * Tests all REST endpoints for managing and ingesting runbooks.
 */
@ExtendWith(MockitoExtension.class)
public class RunbookControllerTest {

    @Mock
    private RunbookRepository runbookRepository;

    @Mock
    private RunbookIngestionService runbookIngestionService;

    @InjectMocks
    private RunbookController runbookController;

    private Runbook testRunbook;
    private UUID testRunbookId;

    @BeforeEach
    void setUp() {
        testRunbookId = UUID.randomUUID();
        testRunbook = createTestRunbook(testRunbookId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET /api/v1/runbooks - List all runbooks or filter by service
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testListRunbooks_NoFilter() {
        // Arrange
        List<Runbook> runbooks = List.of(
            testRunbook,
            createTestRunbook(UUID.randomUUID())
        );
        when(runbookRepository.findAll()).thenReturn(runbooks);

        // Act
        List<Runbook> result = runbookController.listRunbooks(null);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(runbooks);
        verify(runbookRepository).findAll();
        verify(runbookRepository, never()).findByService(anyString());
    }

    @Test
    void testListRunbooks_FilterByService() {
        // Arrange
        String service = "payment-service";
        List<Runbook> paymentServiceRunbooks = List.of(testRunbook);
        when(runbookRepository.findByService(service)).thenReturn(paymentServiceRunbooks);

        // Act
        List<Runbook> result = runbookController.listRunbooks(service);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getService()).isEqualTo(service);
        verify(runbookRepository).findByService(service);
        verify(runbookRepository, never()).findAll();
    }

    @Test
    void testListRunbooks_EmptyList() {
        // Arrange
        when(runbookRepository.findAll()).thenReturn(List.of());

        // Act
        List<Runbook> result = runbookController.listRunbooks(null);

        // Assert
        assertThat(result).isEmpty();
        verify(runbookRepository).findAll();
    }

    @Test
    void testListRunbooks_NoResultsForService() {
        // Arrange
        String unknownService = "unknown-service";
        when(runbookRepository.findByService(unknownService)).thenReturn(List.of());

        // Act
        List<Runbook> result = runbookController.listRunbooks(unknownService);

        // Assert
        assertThat(result).isEmpty();
        verify(runbookRepository).findByService(unknownService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET /api/v1/runbooks/{id} - Get specific runbook by ID
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testGetRunbook_Found() {
        // Arrange
        when(runbookRepository.findById(testRunbookId)).thenReturn(Optional.of(testRunbook));

        // Act
        ResponseEntity<Runbook> response = runbookController.getRunbook(testRunbookId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(testRunbook);
        verify(runbookRepository).findById(testRunbookId);
    }

    @Test
    void testGetRunbook_NotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(runbookRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Runbook> response = runbookController.getRunbook(nonExistentId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        verify(runbookRepository).findById(nonExistentId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // POST /api/v1/runbooks - Ingest new runbook
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testIngestRunbook_Success() {
        // Arrange
        RunbookController.RunbookRequest request = new RunbookController.RunbookRequest();
        request.setTitle("Database Connection Pooling Guide");
        request.setService("payment-service");
        request.setContent("Detailed content about connection pooling...");
        request.setTags(new String[]{"database", "performance", "pooling"});

        Runbook ingestedRunbook = Runbook.builder()
            .id(UUID.randomUUID())
            .title(request.getTitle())
            .service(request.getService())
            .content(request.getContent())
            .tags(request.getTags())
            .build();

        when(runbookIngestionService.ingest(
            request.getTitle(),
            request.getService(),
            request.getContent(),
            request.getTags()
        )).thenReturn(ingestedRunbook);

        // Act
        ResponseEntity<Runbook> response = runbookController.ingestRunbook(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo(request.getTitle());
        assertThat(response.getBody().getService()).isEqualTo(request.getService());
        assertThat(response.getBody().getTags()).containsExactly("database", "performance", "pooling");

        verify(runbookIngestionService).ingest(
            request.getTitle(),
            request.getService(),
            request.getContent(),
            request.getTags()
        );
    }

    @Test
    void testIngestRunbook_WithoutTags() {
        // Arrange
        RunbookController.RunbookRequest request = new RunbookController.RunbookRequest();
        request.setTitle("Simple Runbook");
        request.setService("auth-service");
        request.setContent("Content");
        request.setTags(null);

        Runbook ingestedRunbook = Runbook.builder()
            .id(UUID.randomUUID())
            .title(request.getTitle())
            .service(request.getService())
            .content(request.getContent())
            .tags(null)
            .build();

        when(runbookIngestionService.ingest(
            request.getTitle(),
            request.getService(),
            request.getContent(),
            null
        )).thenReturn(ingestedRunbook);

        // Act
        ResponseEntity<Runbook> response = runbookController.ingestRunbook(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTags()).isNull();
        verify(runbookIngestionService).ingest(
            request.getTitle(),
            request.getService(),
            request.getContent(),
            null
        );
    }

    @Test
    void testIngestRunbook_CallsServiceWithCorrectParameters() {
        // Arrange
        RunbookController.RunbookRequest request = new RunbookController.RunbookRequest();
        request.setTitle("Test Title");
        request.setService("test-service");
        request.setContent("Test Content");
        request.setTags(new String[]{"tag1", "tag2"});

        Runbook ingestedRunbook = createTestRunbook(UUID.randomUUID());
        when(runbookIngestionService.ingest(anyString(), anyString(), anyString(), any(String[].class)))
            .thenReturn(ingestedRunbook);

        // Act
        runbookController.ingestRunbook(request);

        // Assert - Verify parameters passed correctly
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> serviceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> tagsCaptor = ArgumentCaptor.forClass(String[].class);

        verify(runbookIngestionService).ingest(
            titleCaptor.capture(),
            serviceCaptor.capture(),
            contentCaptor.capture(),
            tagsCaptor.capture()
        );

        assertThat(titleCaptor.getValue()).isEqualTo("Test Title");
        assertThat(serviceCaptor.getValue()).isEqualTo("test-service");
        assertThat(contentCaptor.getValue()).isEqualTo("Test Content");
        assertThat(tagsCaptor.getValue()).containsExactly("tag1", "tag2");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // POST /api/v1/runbooks/{id}/re-embed - Re-generate embedding
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testReEmbed_Success() {
        // Arrange
        Runbook reEmbeddedRunbook = createTestRunbook(testRunbookId);
        float[] newEmbedding = createTestEmbedding();
        reEmbeddedRunbook.setEmbedding(newEmbedding);

        when(runbookIngestionService.reEmbed(testRunbookId)).thenReturn(reEmbeddedRunbook);

        // Act
        ResponseEntity<Runbook> response = runbookController.reEmbed(testRunbookId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmbedding()).isEqualTo(newEmbedding);
        verify(runbookIngestionService).reEmbed(testRunbookId);
    }

    @Test
    void testReEmbed_CallsService() {
        // Arrange
        Runbook reEmbeddedRunbook = createTestRunbook(testRunbookId);
        when(runbookIngestionService.reEmbed(testRunbookId)).thenReturn(reEmbeddedRunbook);

        // Act
        runbookController.reEmbed(testRunbookId);

        // Assert
        verify(runbookIngestionService).reEmbed(testRunbookId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/runbooks/{id} - Delete runbook
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testDeleteRunbook_Success() {
        // Arrange
        when(runbookRepository.existsById(testRunbookId)).thenReturn(true);

        // Act
        ResponseEntity<Void> response = runbookController.deleteRunbook(testRunbookId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(runbookRepository).existsById(testRunbookId);
        verify(runbookRepository).deleteById(testRunbookId);
    }

    @Test
    void testDeleteRunbook_NotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(runbookRepository.existsById(nonExistentId)).thenReturn(false);

        // Act
        ResponseEntity<Void> response = runbookController.deleteRunbook(nonExistentId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        verify(runbookRepository).existsById(nonExistentId);
        verify(runbookRepository, never()).deleteById(nonExistentId);
    }

    @Test
    void testDeleteRunbook_VerifiesExistenceBeforeDeletion() {
        // Arrange
        when(runbookRepository.existsById(testRunbookId)).thenReturn(true);

        // Act
        runbookController.deleteRunbook(testRunbookId);

        // Assert - Verify order: check existence first, then delete
        InOrder inOrder = inOrder(runbookRepository);
        inOrder.verify(runbookRepository).existsById(testRunbookId);
        inOrder.verify(runbookRepository).deleteById(testRunbookId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────────

    private Runbook createTestRunbook(UUID id) {
        return Runbook.builder()
            .id(id)
            .title("Connection Pooling Best Practices")
            .service("payment-service")
            .content("This runbook explains how to properly configure connection pooling for databases...")
            .embedding(createTestEmbedding())
            .tags(new String[]{"database", "performance", "connection-pool"})
            .build();
    }

    private float[] createTestEmbedding() {
        float[] embedding = new float[1536]; // OpenAI embedding dimension
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() - 0.5);
        }
        return embedding;
    }
}

