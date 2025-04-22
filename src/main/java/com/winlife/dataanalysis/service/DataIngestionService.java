package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.ErroredGameEvent;
import com.winlife.dataanalysis.model.DebugGameEvent; // Import DebugGameEvent
import org.springframework.data.domain.Page;

import java.util.Map;

public interface DataIngestionService {
    boolean processGameEvent(GameEventDTO event);
    Page<GameEvent> getRecentGameEvents(int page, int size);
    Page<ErroredGameEvent> getRecentErroredEvents(int page, int size);
    Page<DebugGameEvent> getRecentDebugEvents(int page, int size, String deviceId); // Add new method with deviceId filter
    String validateUserProperties(Map<String, Object> userProperties); // 新增用户属性验证方法

}