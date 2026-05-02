package com.rca.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_name", nullable = false)
    private String alertName;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "metric_name")
    private String metricName;

    @Column(name = "threshold")
    private String threshold;

    @Column(name = "current_value")
    private String currentValue;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "environment")
    private String environment = "production";

    @Column(name = "alert_text", nullable = false, columnDefinition = "TEXT")
    private String alertText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum AlertStatus {
        PENDING, PROCESSING, RESOLVED
    }
}
