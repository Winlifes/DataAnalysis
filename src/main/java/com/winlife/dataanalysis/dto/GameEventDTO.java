// src/main/java/com/winlife/dataanalysis/dto/GameEventDTO.java
package com.winlife.dataanalysis.dto;

import lombok.Data;

import java.util.Map;

@Data
public class GameEventDTO {
    private String userId; // 用户唯一标识
    private String deviceId; // 设备唯一标识
    private long timestamp; // 事件发生的 Unix 时间戳 (毫秒)
    private String eventName; // 事件名称 (例如: "level_started", "item_purchased")
    private Map<String, Object> parameters; // 事件相关的参数 (例如: level: 1, item_id: "sword")
}