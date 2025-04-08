package com.winlife.dataanalysis.dto;

import lombok.Data;

@Data
public class PasswordChangeRequest {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
