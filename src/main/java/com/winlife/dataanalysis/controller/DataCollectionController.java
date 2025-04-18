package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.service.DataIngestionService; // Import the new service
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataCollectionController {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectionController.class);

    private final DataIngestionService dataIngestionService; // Inject the service

    /**
     * 接收来自游戏客户端的事件数据
     * @param event GameEventDTO 对象，包含事件详情
     */
    @PostMapping("/event")
    public void collectEvent(@RequestBody GameEventDTO event) {
        logger.info("Received game event: {}", event);

        // Pass the DTO to the service for processing and storage
        dataIngestionService.processGameEvent(event);
    }

    /**
     * 获取最近的游戏事件数据（分页）
     * @param page 页码 (从0开始)
     * @param size 每页数量
     * @return 分页的游戏事件数据
     */
    @GetMapping("/realtime")
    public Page<GameEvent> getRealtimeEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Use the service to fetch paginated and sorted data
        return dataIngestionService.getRecentGameEvents(page, size);
    }

    // TODO: 可以根据需要增加其他数据收集端点
}