// src/main/java/com/winlife/dataanalysis/model/ErroredGameEvent.java
package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "errored_game_events") // Table for errored events
@Data
@NoArgsConstructor
public class ErroredGameEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String deviceId;
    private long timestamp; // Timestamp of the original event

    @Column(name = "event_name")
    private String eventName;

    @Column(columnDefinition = "JSON") // Store the original parameters as JSON
    private String rawParameters; // Store the original raw parameters

    @Column(columnDefinition = "TEXT") // Store the reason for the error
    private String errorReason;

    private long receivedTimestamp; // Timestamp when the event was received by the backend
}