package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.dto.EventAnalysisQuery;
import com.winlife.dataanalysis.dto.EventReportStatistic;
import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.*;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DataIngestionService {
    boolean processGameEvent(GameEventDTO event);
    Page<GameEvent> getRecentGameEvents(int page, int size);
    Page<ErroredGameEvent> getRecentErroredEvents(int page, int size);
    Page<DebugGameEvent> getRecentDebugEvents(int page, int size, String deviceId); // Add new method with deviceId filter
    String validateUserProperties(Map<String, Object> userProperties); // 新增用户属性验证方法
    /**
     * 获取指定时间范围内的事件上报统计数据
     * @param startTime 开始时间戳 (毫秒)
     * @param endTime 结束时间戳 (毫秒)
     * @return 事件上报统计列表
     */
    List<EventReportStatistic> getEventReportingStatistics(long startTime, long endTime); // Add this new method

    List<PlayerData> getPlayerDataByUserId(String userId);

    List<PlayerData> getPlayerDataByDeviceId(String deviceId);

    List<PlayerData> getPlayerDataByUserProperty(String propertyKey, String propertyValue);

    /**
     * 获取指定用户在指定时间范围内的事件上报统计数据。
     * @param userId 用户ID
     * @param startTime 开始时间戳 (毫秒)
     * @param endTime 结束时间戳 (毫秒)
     * @return 事件上报统计列表
     */
    List<EventReportStatistic> getUserEventStatistics(String userId, long startTime, long endTime);

    /**
     * 获取指定用户的行为序列（分页，按时间倒序）。
     * @param userId 用户ID
     * @param page 页码 (从0开始)
     * @param size 每页数量
     * @return 分页的事件数据
     */
    Page<GameEvent> getUserEventSequence(String userId, int page, int size);

    /**
     * Get EventSchema by event name.
     * @param eventName The name of the event.
     * @return Optional containing the EventSchema if found, otherwise empty.
     */
    Optional<EventSchema> getEventSchemaByName(String eventName); // Add this method signature

    /**
     * Executes an event analysis query.
     * This involves parsing the query definition, building dynamic SQL,
     * executing it, and formatting the results.
     * @param query The analysis query definition.
     * @return The analysis results as a list of maps (column_name -> value).
     */
    List<Map<String, Object>> runEventAnalysis(EventAnalysisQuery query); // Add this method signature
}