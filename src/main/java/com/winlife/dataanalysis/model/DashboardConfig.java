package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_config") // Assuming your table is named 'dashboard'
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) // Title should probably be required
    public String title;

    public String size;

    public String chartType;

    @Column(name = "dashboard_id", nullable = false) // Assuming dashboard_id is a foreign key to the dashboard table
    public Long dashboardId;

    @Column(name = "config", columnDefinition = "JSON") // Store as String, rely on JPA/Hibernate type handling or ObjectMapper
    public String config;

    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime createTime;

    // ** NEW FIELD: Add an update time field **
    @Column(name = "update_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
