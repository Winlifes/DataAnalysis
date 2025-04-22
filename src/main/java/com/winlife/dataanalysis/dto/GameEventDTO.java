package com.winlife.dataanalysis.dto;

import lombok.Data;

import java.util.Map;

@Data
public class GameEventDTO {
    private String userId;
    private String deviceId;
    private long timestamp; // Unix timestamp
    private String eventName;
    private Map<String, Object> parameters;
    private int isDebug;
    private Map<String, Object> userProperties; // Add userProperties map
}