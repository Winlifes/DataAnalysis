package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.ErroredGameEvent;
import com.winlife.dataanalysis.model.DebugGameEvent; // Import DebugGameEvent
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        // For debug events, we might still return OK even if not saved to main table,
        // as the debug saving was successful.
        // The boolean 'success' now indicates if it went to the main table.
        // If it went to debug table, success is false.
        // We can choose to return 200 OK if debug saving was successful,
        // or still return 400 if validation failed even in debug mode.
        // Let's return 200 OK if debug mode was active or if it was valid and saved to main.
        // Return 400 only if it was NOT debug mode AND validation failed.

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
}