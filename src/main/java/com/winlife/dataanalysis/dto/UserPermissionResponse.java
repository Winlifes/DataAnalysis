package com.winlife.dataanalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserPermissionResponse {
    private UserPermission[] data;

    @Data
    @AllArgsConstructor
    public static class UserPermission
    {
        private String name;
        private String description;
        private String status;
    }
}
