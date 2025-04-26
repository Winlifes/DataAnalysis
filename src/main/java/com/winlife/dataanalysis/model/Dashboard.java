package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Keeping LocalDateTime as per your provided entity

@Entity
@Table(name = "dashboard") // Assuming your table is named 'dashboard'
@Data
@NoArgsConstructor
@AllArgsConstructor // Keep AllArgsConstructor, Lombok will include new fields
public class Dashboard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_id") // Using snake_case for column name, common in databases
    private Long folderId;

    @Column(nullable = false) // Title should probably be required
    private String title;

    // Keeping LocalDateTime as per your provided entity
    // Using columnDefinition for database default and preventing JPA from managing insert/update
    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime createTime;

    // ** NEW FIELD: Add an update time field **
    @Column(name = "update_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime updateTime;


    // You might also want fields like:
    // private Long userId; // If dashboards are per user
    // private String description; // Optional description

    // Lombok's @Data and @AllArgsConstructor will handle getters/setters and the constructor including the new fields.
}
