package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import org.springframework.data.domain.Page;

public interface DataIngestionService {
    void processGameEvent(GameEventDTO event);
    Page<GameEvent> getRecentGameEvents(int page, int size); // Add new method
}