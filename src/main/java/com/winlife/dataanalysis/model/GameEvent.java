package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "game_events") // Define the table name
@Data
@NoArgsConstructor
public class GameEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String deviceId;
    private long timestamp; // Using long to match the DTO for now

    @Column(name = "event_name")
    private String eventName;

    @Column(columnDefinition = "JSON") // Use JSON type if your MySQL supports it
    // If JSON type is not supported, use @Column(columnDefinition = "TEXT")
    private String parameters; // Store parameters as a JSON string

    @Column(columnDefinition = "JSON") // 新增字段存储用户属性 JSON 字符串
    private String userProperties;
}