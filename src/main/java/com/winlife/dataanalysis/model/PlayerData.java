package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant; // Optional: to store last updated timestamp

@Entity
@Table(name = "player_data") // Define the new table name
@Data
@NoArgsConstructor
public class PlayerData {

    @Id
    private String userId; // Use userId as the primary key

    private String deviceId; // Store the last known device ID for this user

    @Column(columnDefinition = "JSON") // Store user properties as a JSON string
    private String userProperties;

    private long lastUpdatedTimestamp; // Optional: timestamp of the last update

    // Getters and Setters are provided by Lombok's @Data
}