// src/main/java/com/winlife/dataanalysis/controller/EventSchemaController.java
package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.model.EventSchema;
import com.winlife.dataanalysis.repository.EventSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/schemas/events") // 事件结构管理接口路径
@RequiredArgsConstructor
public class EventSchemaController {

    private final EventSchemaRepository eventSchemaRepository;

    /**
     * 获取所有事件结构
     * @return 事件结构列表
     */
    @GetMapping
    public List<EventSchema> getAllEventSchemas() {
        return eventSchemaRepository.findAll();
    }

    /**
     * 根据 ID 获取单个事件结构
     * @param id 事件结构 ID
     * @return 事件结构
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventSchema> getEventSchemaById(@PathVariable Long id) {
        Optional<EventSchema> schema = eventSchemaRepository.findById(id);
        return schema.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 创建新的事件结构
     * @param eventSchema 要创建的事件结构
     * @return 创建成功的事件结构
     */
    @PostMapping
    public ResponseEntity<EventSchema> createEventSchema(@RequestBody EventSchema eventSchema) {
        // 基本验证：事件名称非空且唯一
        if (eventSchema.getEventName() == null || eventSchema.getEventName().trim().isEmpty()) {
            // 返回 400 Bad Request
            return ResponseEntity.badRequest().build();
        }
        if (eventSchemaRepository.findByEventName(eventSchema.getEventName()).isPresent()) {
            // 返回 409 Conflict
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        // TODO: 可以根据需要在这里添加对 parameterSchema JSON 格式的更详细验证

        EventSchema savedSchema = eventSchemaRepository.save(eventSchema);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedSchema);
    }

    /**
     * 更新事件结构
     * @param id 事件结构 ID
     * @param updatedSchema 更新后的事件结构数据
     * @return 更新后的事件结构
     */
    @PutMapping("/{id}")
    public ResponseEntity<EventSchema> updateEventSchema(@PathVariable Long id, @RequestBody EventSchema updatedSchema) {
        Optional<EventSchema> existingSchemaOptional = eventSchemaRepository.findById(id);
        if (existingSchemaOptional.isEmpty()) {
            // 找不到要更新的事件结构，返回 404 Not Found
            return ResponseEntity.notFound().build();
        }
        EventSchema existingSchema = existingSchemaOptional.get();

        // 检查更新后的事件名称是否与现有其他事件名称冲突
        if (!existingSchema.getEventName().equals(updatedSchema.getEventName())) {
            if (eventSchemaRepository.findByEventName(updatedSchema.getEventName()).isPresent()) {
                // 新名称已存在，返回 409 Conflict
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        }

        // 更新字段
        existingSchema.setEventName(updatedSchema.getEventName());
        existingSchema.setParameterSchema(updatedSchema.getParameterSchema());
        // TODO: 可以根据需要在这里添加对 parameterSchema JSON 格式的更详细验证

        EventSchema savedSchema = eventSchemaRepository.save(existingSchema);
        return ResponseEntity.ok(savedSchema);
    }

    /**
     * 删除事件结构
     * @param id 事件结构 ID
     * @return 无内容响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEventSchema(@PathVariable Long id) {
        if (!eventSchemaRepository.existsById(id)) {
            // 找不到要删除的事件结构，返回 404 Not Found
            return ResponseEntity.notFound().build();
        }
        eventSchemaRepository.deleteById(id);
        // 返回 204 No Content
        return ResponseEntity.noContent().build();
    }
}