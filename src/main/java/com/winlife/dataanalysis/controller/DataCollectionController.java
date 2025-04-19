package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.ErroredGameEvent;
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus; // 导入 HttpStatus
import org.springframework.http.ResponseEntity; // 导入 ResponseEntity
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Void> collectEvent(@RequestBody GameEventDTO event) { // 返回类型改为 ResponseEntity<Void>
        logger.info("Received game event: {}", event);
        boolean success = dataIngestionService.processGameEvent(event);

        if (success) {
            // 事件有效并成功存入主表
            return ResponseEntity.ok().build(); // 返回 200 OK
        } else {
            // 事件无效或存入主表失败 (但已存入错误表)
            // 返回 400 Bad Request 指示客户端发送的数据有问题
            return ResponseEntity.badRequest().build(); // 返回 400 Bad Request
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
    @GetMapping("/errored") // 错误数据接口
    public Page<ErroredGameEvent> getRecentErroredEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return dataIngestionService.getRecentErroredEvents(page, size);
    }
}