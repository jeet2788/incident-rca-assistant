package com.rca.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rca_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcaReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "impact", columnDefinition = "TEXT")
    private String impact;

    @Column(name = "timeline", columnDefinition = "TEXT")
    private String timeline;

    @Column(name = "fix_applied", columnDefinition = "TEXT")
    private String fixApplied;

    @Column(name = "prevention", columnDefinition = "TEXT")
    private String prevention;

    @Column(name = "full_report", nullable = false, columnDefinition = "TEXT")
    private String fullReport;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources_used", columnDefinition = "jsonb")
    private String sourcesUsed;

    @Column(name = "engineer_feedback", columnDefinition = "TEXT")
    private String engineerFeedback;

    @Column(name = "feedback_score")
    private Integer feedbackScore;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private RcaStatus status = RcaStatus.DRAFT;

    @Column(name = "slack_ts")
    private String slackTs;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum RcaStatus {
        DRAFT, PUBLISHED, CLOSED
    }
}
