package com.winlife.dataanalysis.model;

import jakarta.persistence.*;
import lombok.Data;


@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column
    private String nickname;

    @Column
    private String phone;

    @Column
    private String email;

    @Column
    private boolean authCheck;

    @Column
    private boolean authEdit;

    @Column
    private boolean authExport;

    @Column
    private boolean authAdmin;
}
