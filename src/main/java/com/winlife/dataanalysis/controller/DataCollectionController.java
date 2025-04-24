package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.EventReportStatistic;
import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.*;
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataCollectionController {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectionController.class);

    private final DataIngestionService dataIngestionService;

    /**
     * 接收来自游戏客户端的事件数据，并根据处理结果返回不同状态码。
     * @param event GameEventDTO 对象，包含事件详情
     * @return ResponseEntity 包含处理结果的状态码
     */
    @PostMapping("/event")
    public ResponseEntity<Void> collectEvent(@RequestBody GameEventDTO event) {
        logger.error("Received game event: {}", event);
        // processGameEvent now handles debug mode internally
        boolean success = dataIngestionService.processGameEvent(event);

        if (event.getIsDebug() == 1) {
            // In debug mode, saving to debug table is considered successful handling
            return ResponseEntity.ok().build(); // Return 200 OK
        } else {
            // Not in debug mode, success means saved to main table
            if (success) {
                return ResponseEntity.ok().build(); // Return 200 OK
            } else {
                // Not in debug mode AND failed validation/ingestion
                return ResponseEntity.badRequest().build(); // Return 400 Bad Request
            }
        }
    }

    /**
     * 获取最近的正常入库游戏事件数据（分页）
     * @param page 页码 (从0开始)
     * @param size 每页数量
     * @return 分页的游戏事件数据
     */
    @GetMapping("/realtime")
    public Page<GameEvent> getRealtimeEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return dataIngestionService.getRecentGameEvents(page, size);
    }

    /**
     * 获取最近的错误游戏事件数据（分页）
     * @param page 页码 (从0开始)
     * @param size 每页数量
     * @return 分页的错误游戏事件数据
     */
    @GetMapping("/errored")
    public Page<ErroredGameEvent> getRecentErroredEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return dataIngestionService.getRecentErroredEvents(page, size);
    }

    /**
     * 获取最近的 Debug 模式游戏事件数据（分页，可按设备ID过滤）
     * @param page 页码 (从0开始)
     * @param size 每页数量
     * @param deviceId 设备ID过滤 (可选)
     * @return 分页的 Debug 游戏事件数据
     */
    @GetMapping("/debug") // New endpoint for debug data
    public Page<DebugGameEvent> getRecentDebugEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String deviceId) { // Optional deviceId
        return dataIngestionService.getRecentDebugEvents(page, size, deviceId);
    }

    // In DataCollectionController.java or a new ReportingController.java
    @GetMapping("/report/statistics")
    public List<EventReportStatistic> getEventReportingStatistics(
            @RequestParam long startTime, // Unix timestamp in seconds
            @RequestParam long endTime) { // Unix timestamp in seconds
        // Call a new service method to get the statistics
        return dataIngestionService.getEventReportingStatistics(startTime, endTime);
    }

    /**
     * 获取指定用户ID在指定时间范围内的事件上报统计数据
     * @param userId 用户ID
     * @param startTime 开始时间戳 (毫秒)
     * @param endTime 结束时间戳 (毫秒)
     * @return 事件上报统计列表
     */
    @GetMapping("/events/statistics/{userId}") // New endpoint for user-specific statistics
    public List<EventReportStatistic> getUserEventStatistics(
            @PathVariable String userId,
            @RequestParam long startTime, // Unix timestamp in milliseconds
            @RequestParam long endTime) { // Unix timestamp in milliseconds
        logger.debug("Received request for event statistics for userId: {} from {} to {}", userId, startTime, endTime);
        return dataIngestionService.getUserEventStatistics(userId, startTime, endTime); // Need to add this method
    }

    /**
     * 获取指定用户ID的行为序列（分页）
     * @param userId 用户ID
     * @param page 页码 (从0开始)
     * @param size 每页数量
     * @return 分页的事件数据
     */
    @GetMapping("/events/sequence/{userId}") // New endpoint for user-specific event sequence
    public Page<GameEvent> getUserEventSequence(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) { // Default size matches frontend initial load
        logger.debug("Received request for event sequence for userId: {} page {} size {}", userId, page, size);
        return dataIngestionService.getUserEventSequence(userId, page, size); // Need to add this method
    }

    @GetMapping("/player")
    public List<PlayerData> searchPlayerData(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String userPropertyKey, // New param
            @RequestParam(required = false) String userPropertyValue) { // New param

        if (StringUtils.hasText(userId)) {
            return dataIngestionService.getPlayerDataByUserId(userId);
        } else if (StringUtils.hasText(deviceId)) {
            return dataIngestionService.getPlayerDataByDeviceId(deviceId);
        } else if (StringUtils.hasText(userPropertyKey) && StringUtils.hasText(userPropertyValue)) {
            // Call the new method for user property search
            return dataIngestionService.getPlayerDataByUserProperty(userPropertyKey, userPropertyValue); // Need to add this method
        }
        else {
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定用户ID的玩家数据（最新属性等）
     * @param userId 用户ID
     * @return 玩家数据或 404
     */
    @GetMapping("/player/{userId}") // New endpoint for fetching single player data
    public ResponseEntity<PlayerData> getPlayerData(@PathVariable String userId) {
        logger.debug("Received request to get PlayerData for userId: {}", userId);
        List<PlayerData> playerDatas = dataIngestionService.getPlayerDataByUserId(userId);
        PlayerData playerData = null;
        if (playerDatas != null && !playerDatas.isEmpty()) {
            playerData = playerDatas.stream().findFirst().orElse(null);
        }
        if (playerData != null) {
            return ResponseEntity.ok(playerData); // Return 200 OK with data
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // Return 404 Not Found
        }
    }
}