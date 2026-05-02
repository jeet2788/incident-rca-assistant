package com.rca.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident_embeddings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Column(name = "alert_text", nullable = false, columnDefinition = "TEXT")
    private String alertText;

    // Stored as float array; pgvector handles conversion via custom type
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "service")
    private String service;

    @Column(name = "severity")
    private String severity;

    @Column(name = "rca_summary", columnDefinition = "TEXT")
    private String rcaSummary;

    @Column(name = "feedback_score")
    private Integer feedbackScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
