package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant; // Optional: add timestamps for creation/update tracking

@Entity
@Table(name = "metric_definitions") // Table name
@Data
@NoArgsConstructor
public class MetricDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-generated primary key
    private Long id;

    @Column(unique = true, nullable = false) // Metric name must be unique and not null
    private String name;

    @Column(nullable = false) // Description is required
    private String description;

    private String unit; // Unit might be optional

    // Store the metric definition. Using TEXT or CLOB for potentially long definitions.
    // If using a structured JSON definition, consider JSON type if database supports and ORM maps it.
    @Column(columnDefinition = "TEXT", nullable = false) // Definition is required
    private String definition;

    // Optional: Timestamps for tracking
    private Instant createdAt;
    private Instant updatedAt;

    // Lifecycle callbacks for timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}