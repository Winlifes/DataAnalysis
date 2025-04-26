package com.winlife.dataanalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String status;
    private String message;
    private String token;
    private String nickname;
    private Boolean isSuperAdmin;
    private Boolean isExported;
    private Boolean isEdit;
    private Boolean isCheck;
}