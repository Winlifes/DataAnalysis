package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.ErroredGameEvent;
import org.springframework.data.domain.Page;

public interface DataIngestionService {
    /**
     * 处理游戏事件，进行验证并存储到对应的表。
     * @param event 游戏事件 DTO。
     * @return true 如果事件有效并成功存入主表 (game_events)，
     * false 如果事件无效或存入主表失败 (并已存入错误表 errored_game_events)。
     */
    boolean processGameEvent(GameEventDTO event); // 返回类型改为 boolean
    Page<GameEvent> getRecentGameEvents(int page, int size);
    Page<ErroredGameEvent> getRecentErroredEvents(int page, int size);
}