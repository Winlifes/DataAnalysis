package com.winlife.dataanalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoResponse {
    private String username;
    private String nickname;
    private String phone;
    private String email;
}
