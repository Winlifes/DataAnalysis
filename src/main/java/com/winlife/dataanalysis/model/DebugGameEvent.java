package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "debug_game_events")
@Data
@NoArgsConstructor
public class DebugGameEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String deviceId;
    private long timestamp; // Timestamp of the original event

    @Column(name = "event_name")
    private String eventName;

    @Column(columnDefinition = "JSON")
    private String rawParameters; // Store the original raw event parameters

    @Column(columnDefinition = "JSON") // Add column for user properties
    private String rawUserProperties; // Store the original raw user properties

    private boolean isValid; // Whether the event was valid according to schema
    @Column(columnDefinition = "TEXT")
    private String validationError; // Reason for validation failure if not valid (for event and user properties)

    private long receivedTimestamp; // Timestamp when the event was received by the backend
}