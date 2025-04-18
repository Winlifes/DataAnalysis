// src/main/java/com/winlife/dataanalysis/service/impl/DataIngestionServiceImpl.java
package com.winlife.dataanalysis.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode; // 导入 JsonNode
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.EventSchema; // 导入 EventSchema
import com.winlife.dataanalysis.repository.GameEventRepository;
import com.winlife.dataanalysis.repository.EventSchemaRepository; // 导入 EventSchemaRepository
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Iterator; // 导入 Iterator
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DataIngestionServiceImpl implements DataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionServiceImpl.class);
    private final GameEventRepository gameEventRepository;
    private final EventSchemaRepository eventSchemaRepository; // 注入 EventSchemaRepository
    private final ObjectMapper objectMapper; // 注入 ObjectMapper

    @Override
    public void processGameEvent(GameEventDTO event) {
        // 1. 验证事件是否符合预设的 Schema
        boolean isValid = validateEvent(event);

        if (!isValid) {
            // 如果验证失败，记录警告日志并跳过保存
            logger.warn("Invalid game event received based on schema validation: {}", event);
            // TODO: 根据业务需求处理验证失败的事件，例如记录到另一个表、发送告警等
            return;
        }

        // 2. 如果验证通过，保存事件
        GameEvent gameEvent = new GameEvent();
        gameEvent.setUserId(event.getUserId());
        gameEvent.setDeviceId(event.getDeviceId());
        gameEvent.setTimestamp(event.getTimestamp());
        gameEvent.setEventName(event.getEventName());

        // 将参数 Map 转换为 JSON 字符串进行存储
        try {
            gameEvent.setParameters(objectMapper.writeValueAsString(event.getParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event parameters for event: {}", event.getEventName(), e);
            // 如果序列化失败，将参数设置为 null 或空字符串，并记录错误
            gameEvent.setParameters(null);
        }

        try {
            gameEventRepository.save(gameEvent);
            logger.debug("Successfully saved valid game event: {}", event.getEventName());
        } catch (Exception e) {
            logger.error("Failed to save game event: {}", event.getEventName(), e);
            // 处理数据库保存错误
        }
    }

    @Override
    public Page<GameEvent> getRecentGameEvents(int page, int size) {
        // Create a PageRequest for pagination, sorting by timestamp descending
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return gameEventRepository.findAll(pageRequest);
    }

    /**
     * 验证游戏事件是否符合定义的 Schema
     * @param event 接收到的游戏事件 DTO
     * @return 如果事件符合 Schema 或没有定义 Schema 则返回 true，否则返回 false
     */
    private boolean validateEvent(GameEventDTO event) {
        // 基本非空检查
        if (event == null) {
            logger.warn("Received null event for validation.");
            return false;
        }
        if (event.getEventName() == null || event.getEventName().trim().isEmpty()) {
            logger.warn("Received event with empty or null event name: {}", event);
            return false;
        }


        // 根据事件名称查找对应的 Schema
        Optional<EventSchema> schemaOptional = eventSchemaRepository.findByEventName(event.getEventName());

        // 策略：如果找不到对应的 Schema，默认允许该事件通过（也可以设置为拒绝，取决于产品需求）
        if (schemaOptional.isEmpty()) {
            logger.warn("No schema found for event: {}. Allowing event.", event.getEventName());
            return true;
        }

        EventSchema schema = schemaOptional.get();
        Map<String, Object> receivedParameters = event.getParameters();

        // 策略：如果 Schema 定义没有参数（parameterSchema 为 null、空字符串或 {}），
        // 则接收到的参数也必须为 null 或空
        if (schema.getParameterSchema() == null || schema.getParameterSchema().trim().isEmpty() || "{}".equals(schema.getParameterSchema().trim())) {
            if (receivedParameters != null && !receivedParameters.isEmpty()) {
                logger.warn("Event '{}' has parameters but schema expects none. Received parameters: {}", event.getEventName(), receivedParameters);
                return false; // 接收到参数但 Schema 期望没有，拒绝
            }
            return true; // 验证通过
        }


        JsonNode schemaNode;
        try {
            // 解析 Schema 的 JSON 字符串
            schemaNode = objectMapper.readTree(schema.getParameterSchema());
            // Schema 必须是一个 JSON 对象
            if (!schemaNode.isObject()) {
                logger.error("Event schema for '{}' is not a valid JSON object: {}", event.getEventName(), schema.getParameterSchema());
                return false; // Schema 格式错误，拒绝
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse parameter schema JSON for event: {}", event.getEventName(), e);
            return false; // Schema JSON 格式错误，拒绝
        } catch (Exception e) {
            logger.error("Error processing event schema for event: {}", event.getEventName(), e);
            return false; // 其他 Schema 处理错误，拒绝
        }


        // 验证接收到的参数是否符合 Schema 定义
        // 遍历接收到的参数
        if (receivedParameters != null) {
            for (Map.Entry<String, Object> receivedParam : receivedParameters.entrySet()) {
                String paramName = receivedParam.getKey(); // 参数名称
                Object receivedValue = receivedParam.getValue(); // 参数值

                // 在 Schema 中查找对应的参数定义
                JsonNode paramSchema = schemaNode.get(paramName);

                if (paramSchema == null) {
                    // 接收到 Schema 中未定义的参数
                    logger.warn("Event '{}' has unexpected parameter: {}", event.getEventName(), paramName);
                    // 策略：不允许出现 Schema 中未定义的参数
                    return false; // 拒绝
                }

                // 解析 Schema 中参数定义的类型和是否必需
                String expectedType = null;
                boolean isRequired = false;

                if (paramSchema.isTextual()) {
                    // Schema 定义为简单类型字符串，例如 "string"
                    expectedType = paramSchema.asText().toLowerCase();
                } else if (paramSchema.isObject()) {
                    // Schema 定义为对象，例如 {"type": "string", "required": true}
                    if (!paramSchema.has("type")) {
                        logger.warn("Parameter '{}' in schema for event '{}' is object but missing 'type'.", paramName, event.getEventName());
                        return false; // Schema 格式错误，缺少 type，拒绝
                    }
                    expectedType = paramSchema.get("type").asText().toLowerCase();
                    isRequired = paramSchema.has("required") ? paramSchema.get("required").asBoolean() : false;
                } else {
                    logger.warn("Parameter '{}' in schema for event '{}' is not text or object.", paramName, event.getEventName());
                    return false; // Schema 格式错误，参数定义不是字符串或对象，拒绝
                }

                // 检查必需参数的值是否为 null
                if (isRequired && receivedValue == null) {
                    logger.warn("Event '{}' is missing required parameter: {} (value is null)", event.getEventName(), paramName);
                    return false; // 必需参数值为 null，拒绝
                }

                // 基本类型验证（如果接收到的值不为 null）
                if (receivedValue != null) {
                    boolean typeMatch = false;
                    switch (expectedType) {
                        case "string":
                            typeMatch = receivedValue instanceof String;
                            break;
                        case "integer":
                            // 接受 Integer 或 Long
                            typeMatch = receivedValue instanceof Integer || receivedValue instanceof Long;
                            break;
                        case "float":
                        case "double":
                            // 接受 Float 或 Double
                            typeMatch = receivedValue instanceof Float || receivedValue instanceof Double;
                            break;
                        case "boolean":
                            typeMatch = receivedValue instanceof Boolean;
                            break;
                        // TODO: 根据需要添加更多类型检查，例如数组、嵌套对象等
                        default:
                            logger.warn("Unknown expected type '{}' for parameter '{}' in schema for event '{}'.", expectedType, paramName, event.getEventName());
                            // 策略：Schema 中出现未知类型，拒绝此事件
                            return false; // 未知类型，拒绝
                    }

                    if (!typeMatch) {
                        // 类型不匹配
                        logger.warn("Event '{}' parameter '{}' has incorrect type. Expected: {}, Received: {} (Java type: {})",
                                event.getEventName(), paramName, expectedType, receivedValue, receivedValue.getClass().getSimpleName());
                        return false; // 类型不匹配，拒绝
                    }
                }
            }
        }


        // 验证所有 Schema 中定义的必需参数是否都已接收
        if (schemaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String paramName = field.getKey();
                JsonNode paramSchema = field.getValue();

                boolean isRequired = false;
                // 只有在 Schema 定义为对象且包含 "required: true" 时，参数才被认为是必需的
                if (paramSchema.isObject() && paramSchema.has("required")) {
                    isRequired = paramSchema.get("required").asBoolean();
                }

                if (isRequired) {
                    // 检查接收到的参数 Map 是否包含此必需参数
                    // receiveParameters == null 表示根本没有参数
                    // !receiveParameters.containsKey(paramName) 表示参数名称不存在
                    // receiveParameters.get(paramName) == null 表示参数存在但值为 null (已在上面循环中检查)
                    if (receivedParameters == null || !receivedParameters.containsKey(paramName)) {
                        logger.warn("Event '{}' is missing required parameter: {}", event.getEventName(), paramName);
                        return false; // 缺少必需参数，拒绝
                    }
                }
            }
        }


        return true; // 所有验证通过，事件有效
    }
}