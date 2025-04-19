package com.winlife.dataanalysis.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.EventSchema;
import com.winlife.dataanalysis.model.ErroredGameEvent;
import com.winlife.dataanalysis.repository.GameEventRepository;
import com.winlife.dataanalysis.repository.EventSchemaRepository;
import com.winlife.dataanalysis.repository.ErroredGameEventRepository;
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DataIngestionServiceImpl implements DataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionServiceImpl.class);
    private final GameEventRepository gameEventRepository;
    private final EventSchemaRepository eventSchemaRepository;
    private final ErroredGameEventRepository erroredGameEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional // 确保事务注解存在
    public boolean processGameEvent(GameEventDTO event) { // 返回类型改为 boolean
        String validationError = validateEvent(event); // validateEvent 返回错误原因字符串或 null

        if (validationError != null) {
            logger.warn("Invalid game event received based on schema validation: {}. Reason: {}", event, validationError);
            saveErroredEvent(event, validationError);
            // 返回 false 表示验证失败或已存入错误表
            return false;
        }

        // 2. Save event if valid
        GameEvent gameEvent = new GameEvent();
        gameEvent.setUserId(event.getUserId());
        gameEvent.setDeviceId(event.getDeviceId());
        gameEvent.setTimestamp(event.getTimestamp());
        gameEvent.setEventName(event.getEventName());

        try {
            gameEvent.setParameters(objectMapper.writeValueAsString(event.getParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event parameters for valid event {}: {}", event.getEventName(), event.getParameters(), e);
            // 如果有效事件的参数序列化失败，视为入库异常，保存到错误表并返回 false
            saveErroredEvent(event, "Failed to serialize parameters for valid event: " + e.getMessage());
            return false;
        }

        try {
            gameEventRepository.save(gameEvent);
            logger.debug("Successfully saved valid game event: {}", event.getEventName());
            // 返回 true 表示有效且成功入库
            return true;
        } catch (Exception e) {
            logger.error("Failed to save game event to GameEvent table for event {}: {}", event.getEventName(), e.getMessage(), e);
            // 如果保存到主表失败，保存到错误表并返回 false
            saveErroredEvent(event, "Failed to save to main game_events table: " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存错误的游戏事件到错误表中
     *
     * @param event       原始的 GameEventDTO
     * @param errorReason 验证失败的原因或入库异常原因
     */
    private void saveErroredEvent(GameEventDTO event, String errorReason) {
        ErroredGameEvent erroredEvent = new ErroredGameEvent();
        erroredEvent.setUserId(event.getUserId());
        erroredEvent.setDeviceId(event.getDeviceId());
        erroredEvent.setTimestamp(event.getTimestamp()); // 原始事件时间戳
        erroredEvent.setEventName(event.getEventName());
        erroredEvent.setErrorReason(errorReason);
        // 记录后端接收到这个事件（无论是有效还是无效，只要进入处理流程）的时间
        erroredEvent.setReceivedTimestamp(Instant.now().toEpochMilli());

        try {
            // 尝试将原始参数序列化为 JSON 字符串进行存储
            erroredEvent.setRawParameters(objectMapper.writeValueAsString(event.getParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize raw parameters for errored event {}: {}", event.getEventName(), event.getParameters(), e);
            // 如果原始参数序列化失败，将 rawParameters 设置为 null
            erroredEvent.setRawParameters(null);
            // 注意：这里的序列化失败通常是ObjectMapper配置或数据本身的问题，
            // 如果是因为参数本身格式问题导致序列化失败，可以将错误原因更新。
            erroredEvent.setErrorReason("Failed to serialize raw parameters: " + e.getMessage()); // 可以选择覆盖之前的错误原因
        }

        try {
            logger.debug("Attempting to save errored event with name: {}, reason: {}", erroredEvent.getEventName(), erroredEvent.getErrorReason());
            ErroredGameEvent savedEvent = erroredGameEventRepository.save(erroredEvent);
            logger.debug("Successfully saved errored game event with ID: {}", savedEvent.getId());
        } catch (Exception e) {
            logger.error("Failed to save errored game event for event {}: {}", event.getEventName(), e.getMessage(), e);
            // 打印更详细的错误堆栈
            logger.error("Error details:", e);
        }
    }


    @Override
    public Page<GameEvent> getRecentGameEvents(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return gameEventRepository.findAll(pageRequest);
    }

    @Override
    public Page<ErroredGameEvent> getRecentErroredEvents(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("receivedTimestamp").descending());
        return erroredGameEventRepository.findAll(pageRequest);
    }


    /**
     * Validates the game event against the defined Schema.
     * Returns null if valid, otherwise returns a String describing the validation error.
     *
     * @param event The received game event DTO.
     * @return Null if valid, or error reason string.
     */
    private String validateEvent(GameEventDTO event) {
        // ... (验证逻辑保持不变，确保在返回错误信息时有日志打印) ...

        if (event == null) {
            logger.debug("Validation failed: Received null event.");
            return "Received null event.";
        }
        if (event.getEventName() == null || event.getEventName().trim().isEmpty()) {
            logger.debug("Validation failed: Received event with empty or null event name: {}", event);
            return "Received event with empty or null event name.";
        }


        Optional<EventSchema> schemaOptional = eventSchemaRepository.findByEventName(event.getEventName());

        if (schemaOptional.isEmpty()) {
            logger.debug("No schema found for event: {}. Allowing event.", event.getEventName());
            return "No schema found for event."; // Valid
        }

        EventSchema schema = schemaOptional.get();
        Map<String, Object> receivedParameters = event.getParameters();

        if (schema.getParameterSchema() == null || schema.getParameterSchema().trim().isEmpty() || "{}".equals(schema.getParameterSchema().trim())) {
            if (receivedParameters != null && !receivedParameters.isEmpty()) {
                String reason = "Event has parameters but schema expects none. Received parameters: " + receivedParameters;
                logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                return reason;
            }
            return null; // Valid
        }


        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(schema.getParameterSchema());
            if (!schemaNode.isObject()) {
                String reason = "Event schema for '" + event.getEventName() + "' is not a valid JSON object: " + schema.getParameterSchema();
                logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                return reason;
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse parameter schema JSON for event: {}", event.getEventName(), e);
            String reason = "Failed to parse event schema JSON.";
            logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
            return reason;
        } catch (Exception e) {
            logger.error("Error processing event schema for event: {}", event.getEventName(), e);
            String reason = "Error processing event schema.";
            logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
            return reason;
        }


        if (receivedParameters != null) {
            for (Map.Entry<String, Object> receivedParam : receivedParameters.entrySet()) {
                String paramName = receivedParam.getKey();
                Object receivedValue = receivedParam.getValue();
                JsonNode paramSchema = schemaNode.get(paramName);

                if (paramSchema == null) {
                    String reason = "Event '" + event.getEventName() + "' has unexpected parameter: " + paramName;
                    logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                    return reason;
                }

                String expectedType = null;
                boolean isRequired = false;

                if (paramSchema.isTextual()) {
                    expectedType = paramSchema.asText().toLowerCase();
                } else if (paramSchema.isObject()) {
                    if (!paramSchema.has("type")) {
                        String reason = "Parameter '" + paramName + "' in schema for event '" + event.getEventName() + "' is object but missing 'type'.";
                        logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                        return reason;
                    }
                    expectedType = paramSchema.get("type").asText().toLowerCase();
                    isRequired = paramSchema.has("required") ? paramSchema.get("required").asBoolean() : false;
                } else {
                    String reason = "Parameter '" + paramName + "' in schema for event '" + event.getEventName() + "' is not text or object.";
                    logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                    return reason;
                }

                if (isRequired && receivedValue == null) {
                    String reason = "Event '" + event.getEventName() + "' is missing required parameter: " + paramName + " (value is null)";
                    logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                    return reason;
                }

                if (receivedValue != null) {
                    boolean typeMatch = false;
                    switch (expectedType) {
                        case "string":
                            typeMatch = receivedValue instanceof String;
                            break;
                        case "integer":
                            typeMatch = receivedValue instanceof Integer || receivedValue instanceof Long;
                            break;
                        case "float":
                        case "double":
                            typeMatch = receivedValue instanceof Float || receivedValue instanceof Double;
                            break;
                        case "boolean":
                            typeMatch = receivedValue instanceof Boolean;
                            break;
                        // TODO: Add more type checks
                        default:
                            String reason = "Unknown expected type '" + expectedType + "' for parameter '" + paramName + "' in schema for event '" + event.getEventName() + "'.";
                            logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                            return reason;
                    }

                    if (!typeMatch) {
                        String reason = "Event '" + event.getEventName() + "' parameter '" + paramName + "' has incorrect type. Expected: " + expectedType + ", Received: " + (receivedValue != null ? receivedValue.getClass().getSimpleName() : "null");
                        logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                        return reason;
                    }
                }
            }
        }


        if (schemaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String paramName = field.getKey();
                JsonNode paramSchema = field.getValue();

                boolean isRequired = false;
                if (paramSchema.isObject() && paramSchema.has("required")) {
                    isRequired = paramSchema.get("required").asBoolean();
                }

                if (isRequired) {
                    if (receivedParameters == null || !receivedParameters.containsKey(paramName)) {
                        String reason = "Event '" + event.getEventName() + "' is missing required parameter: " + paramName;
                        logger.debug("Validation failed for event '{}': {}", event.getEventName(), reason);
                        return reason;
                    }
                }
            }
        }

        logger.debug("Validation successful for event '{}'.", event.getEventName());
        return null;
    }
}