package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dashboard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long folderId;

    private String title;

    private LocalDateTime createTime = LocalDateTime.now();
}
