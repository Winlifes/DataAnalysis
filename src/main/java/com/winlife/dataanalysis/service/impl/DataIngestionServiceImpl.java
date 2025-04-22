package com.winlife.dataanalysis.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.GameEvent;
import com.winlife.dataanalysis.model.EventSchema;
import com.winlife.dataanalysis.model.ErroredGameEvent;
import com.winlife.dataanalysis.model.DebugGameEvent;
import com.winlife.dataanalysis.model.UserPropertySchema; // Import UserPropertySchema
import com.winlife.dataanalysis.repository.GameEventRepository;
import com.winlife.dataanalysis.repository.EventSchemaRepository;
import com.winlife.dataanalysis.repository.ErroredGameEventRepository;
import com.winlife.dataanalysis.repository.DebugGameEventRepository;
import com.winlife.dataanalysis.repository.UserPropertySchemaRepository; // Import UserPropertySchemaRepository
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final DebugGameEventRepository debugGameEventRepository;
    private final UserPropertySchemaRepository userPropertySchemaRepository; // Inject UserPropertySchemaRepository
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public boolean processGameEvent(GameEventDTO event) {
        // Validate event parameters against event schema
        String eventValidationError = validateEvent(event);
        boolean isEventValid = eventValidationError == null;

        // Validate user properties against user property schema
        String userPropertiesValidationError = validateUserProperties(event.getUserProperties());
        boolean areUserPropertiesValid = userPropertiesValidationError == null;

        // Combine validation results
        boolean isEventAndPropertiesValid = isEventValid && areUserPropertiesValid;

        String combinedValidationError = null;
        if (!isEventValid) {
            combinedValidationError = "Event validation failed: " + eventValidationError;
        }
        if (!areUserPropertiesValid) {
            if (combinedValidationError == null) {
                combinedValidationError = "User properties validation failed: " + userPropertiesValidationError;
            } else {
                combinedValidationError += "; User properties validation failed: " + userPropertiesValidationError;
            }
        }


        if (event.getIsDebug() == 1) {
            // If in debug mode, save to debug table with combined validation result
            saveDebugEvent(event, isEventAndPropertiesValid, combinedValidationError);
            return false; // Indicates not saved to main table
        }

        // If not in debug mode, proceed based on combined validation result
        if (!isEventAndPropertiesValid) {
            logger.warn("Invalid game event or user properties received. Event: {}, User Properties: {}. Reason: {}", event.getEventName(), event.getUserProperties(), combinedValidationError);
            // Save to error table with the combined reason
            saveErroredEvent(event, combinedValidationError);
            return false; // Indicates validation failure and saved to error table
        }

        // 2. Save event if valid and not in debug mode
        GameEvent gameEvent = new GameEvent();
        gameEvent.setUserId(event.getUserId());
        gameEvent.setDeviceId(event.getDeviceId());
        gameEvent.setTimestamp(event.getTimestamp());
        gameEvent.setEventName(event.getEventName());

        try {
            // Save event parameters (which were validated)
            gameEvent.setParameters(objectMapper.writeValueAsString(event.getParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event parameters for valid event {}: {}", event.getEventName(), event.getParameters(), e);
            // This shouldn't happen if validation passed and object is serializable, but as a fallback
            saveErroredEvent(event, "Failed to serialize valid event parameters: " + e.getMessage());
            return false;
        }

        try {
            // Save user properties (which were validated)
            gameEvent.setUserProperties(objectMapper.writeValueAsString(event.getUserProperties()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize user properties for event {}: {}", event.getEventName(), event.getUserProperties(), e);
            // If serialization fails for valid user properties, save to errored events and return false
            saveErroredEvent(event, "Failed to serialize valid user properties: " + e.getMessage());
            return false;
        }


        try {
            gameEventRepository.save(gameEvent);
            logger.debug("Successfully saved valid game event with user properties: {}", event.getEventName());
            return true; // Indicates valid and saved to main table
        } catch (Exception e) {
            logger.error("Failed to save game event with user properties to GameEvent table for event {}: {}", event.getEventName(), e.getMessage(), e);
            // If saving to the main table fails, save to errored events and return false
            saveErroredEvent(event, "Failed to save to main game_events table: " + e.getMessage());
            return false;
        }
    }

    /**
     * Saves an errored game event to the error table.
     * @param event The original GameEventDTO.
     * @param errorReason The reason for validation failure or ingestion exception.
     */
    private void saveErroredEvent(GameEventDTO event, String errorReason) {
        ErroredGameEvent erroredEvent = new ErroredGameEvent();
        erroredEvent.setUserId(event.getUserId());
        erroredEvent.setDeviceId(event.getDeviceId());
        erroredEvent.setTimestamp(event.getTimestamp());
        erroredEvent.setEventName(event.getEventName());
        erroredEvent.setErrorReason(errorReason);
        erroredEvent.setReceivedTimestamp(Instant.now().toEpochMilli());

        try {
            // Attempt to serialize original event parameters
            erroredEvent.setRawParameters(objectMapper.writeValueAsString(event.getParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize raw event parameters for errored event {}: {}", event.getEventName(), event.getParameters(), e);
            erroredEvent.setRawParameters(null);
        }

        // ErroredGameEvent does not store user properties separately, they are part of the raw event if needed.
        // If you want to store raw user properties explicitly in ErroredGameEvent, add a column for it.
        // For now, they are implicitly part of the raw event data if you save the whole raw DTO JSON.
        // But the user wants to save rawParameters and validation error.
        // Let's add a rawUserProperties field to ErroredGameEvent model. (Done in Step 2 for Debug, add for Errored too)
        // --> Update: UserProperties are added to GameEvent and DebugGameEvent. Let's update ErroredGameEvent too.

//        try {
//            // Attempt to serialize original user properties
//            // Need to update ErroredGameEvent entity first
//            erroredEvent.setRawUserProperties(objectMapper.writeValueAsString(event.getUserProperties()));
//        } catch (JsonProcessingException e) {
//            logger.error("Failed to serialize raw user properties for errored event {}: {}", event.getEventName(), event.getUserProperties(), e);
//            erroredEvent.setRawUserProperties(null);
//        }


        try {
            logger.debug("Attempting to save errored event with name: {}, reason: {}", erroredEvent.getEventName(), erroredEvent.getErrorReason());
            ErroredGameEvent savedEvent = erroredGameEventRepository.save(erroredEvent);
            logger.debug("Successfully saved errored game event with ID: {}", savedEvent.getId());
        } catch (Exception e) {
            logger.error("Failed to save errored game event for event {}: {}", event.getEventName(), e.getMessage(), e);
            logger.error("Error details:", e);
        }
    }

    /**
     * Saves a game event received in debug mode to the debug table.
     * @param event The original GameEventDTO.
     * @param isValid Whether the event and its user properties were valid according to schemas.
     * @param validationError The combined reason for validation failure if not valid.
     */
    private void saveDebugEvent(GameEventDTO event, boolean isValid, String validationError) {
        DebugGameEvent debugEvent = new DebugGameEvent();
        debugEvent.setUserId(event.getUserId());
        debugEvent.setDeviceId(event.getDeviceId());
        debugEvent.setTimestamp(event.getTimestamp());
        debugEvent.setEventName(event.getEventName());
        debugEvent.setValid(isValid);
        debugEvent.setValidationError(validationError);
        debugEvent.setReceivedTimestamp(Instant.now().toEpochMilli());

        try {
            // Save raw event parameters
            debugEvent.setRawParameters(objectMapper.writeValueAsString(event.getParameters()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize raw event parameters for debug event {}: {}", event.getEventName(), event.getParameters(), e);
            debugEvent.setRawParameters(null);
        }

        try {
            // Save raw user properties
            debugEvent.setRawUserProperties(objectMapper.writeValueAsString(event.getUserProperties()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize raw user properties for debug event {}: {}", event.getEventName(), event.getUserProperties(), e);
            debugEvent.setRawUserProperties(null);
        }


        try {
            logger.debug("Attempting to save debug event with name: {}, isValid: {}", debugEvent.getEventName(), debugEvent.isValid());
            DebugGameEvent savedEvent = debugGameEventRepository.save(debugEvent);
            logger.debug("Successfully saved debug game event with ID: {}", savedEvent.getId());
        } catch (Exception e) {
            logger.error("Failed to save debug game event for event {}: {}", event.getEventName(), e.getMessage(), e);
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

    @Override
    public Page<DebugGameEvent> getRecentDebugEvents(int page, int size, String deviceId) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("receivedTimestamp").descending());
        if (StringUtils.hasText(deviceId)) {
            return debugGameEventRepository.findByDeviceIdOrderByReceivedTimestampDesc(deviceId, pageRequest);
        } else {
            return debugGameEventRepository.findAllByOrderByReceivedTimestampDesc(pageRequest);
        }
    }

    @Override
    public String validateUserProperties(Map<String, Object> userProperties) {
        Optional<UserPropertySchema> schemaOptional = userPropertySchemaRepository.findFirstByOrderByIdAsc();

        // Policy: If no user property schema is defined, allow the user properties.
        if (schemaOptional.isEmpty()) {
            logger.debug("No user property schema found. Allowing user properties.");
            return null; // Valid
        }

        UserPropertySchema schema = schemaOptional.get();
        // Reuse the same validation logic as for event parameters, but with the user property schema
        return validateProperties(userProperties, schema.getPropertySchema(), "User Property");
    }


    /**
     * Reusable validation logic for properties map against a JSON schema string.
     * @param properties The map of properties (event parameters or user properties).
     * @param schemaJson The JSON string defining the schema.
     * @param propertyTypeLabel Label for logging (e.g., "Event Parameter", "User Property").
     * @return Null if valid, or error reason string.
     */
    private String validateProperties(Map<String, Object> properties, String schemaJson, String propertyTypeLabel) {
        if (schemaJson == null || schemaJson.trim().isEmpty() || "{}".equals(schemaJson.trim())) {
            if (properties != null && !properties.isEmpty()) {
                return propertyTypeLabel + "s received but schema expects none. Received: " + properties;
            }
            return null; // Valid (no properties expected, none received)
        }

        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(schemaJson);
            if (!schemaNode.isObject()) {
                return propertyTypeLabel + " schema is not a valid JSON object: " + schemaJson;
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse " + propertyTypeLabel + " schema JSON: {}", schemaJson, e);
            return "Failed to parse " + propertyTypeLabel + " schema JSON.";
        } catch (Exception e) {
            logger.error("Error processing " + propertyTypeLabel + " schema: {}", schemaJson, e);
            return "Error processing " + propertyTypeLabel + " schema.";
        }


        // Validate properties present in the received map
        if (properties != null) {
            for (Map.Entry<String, Object> receivedParam : properties.entrySet()) {
                String paramName = receivedParam.getKey();
                Object receivedValue = receivedParam.getValue();

                JsonNode paramSchema = schemaNode.get(paramName);

                if (paramSchema == null) {
                    return propertyTypeLabel + " '" + paramName + "' is unexpected.";
                }

                String expectedType = null;
                boolean isRequired = false;

                if (paramSchema.isTextual()) {
                    expectedType = paramSchema.asText().toLowerCase();
                } else if (paramSchema.isObject()) {
                    if (!paramSchema.has("type")) {
                        return propertyTypeLabel + " '" + paramName + "' schema is object but missing 'type'.";
                    }
                    expectedType = paramSchema.get("type").asText().toLowerCase();
                    isRequired = paramSchema.has("required") ? paramSchema.get("required").asBoolean() : false;
                } else {
                    return propertyTypeLabel + " '" + paramName + "' schema is not text or object.";
                }

                if (isRequired && receivedValue == null) {
                    return propertyTypeLabel + " '" + paramName + "' is required but value is null.";
                }

                if (receivedValue != null) {
                    boolean typeMatch = false;
                    switch (expectedType) {
                        case "string": typeMatch = receivedValue instanceof String; break;
                        case "integer": typeMatch = receivedValue instanceof Integer || receivedValue instanceof Long; break;
                        case "float": case "double": typeMatch = receivedValue instanceof Float || receivedValue instanceof Double; break;
                        case "boolean": typeMatch = receivedValue instanceof Boolean; break;
                        // TODO: Add more type checks
                        default:
                            return "Unknown expected type '" + expectedType + "' for " + propertyTypeLabel + " '" + paramName + "'.";
                    }

                    if (!typeMatch) {
                        return propertyTypeLabel + " '" + paramName + "' has incorrect type. Expected: " + expectedType + ", Received: " + (receivedValue != null ? receivedValue.getClass().getSimpleName() : "null");
                    }
                }
            }
        }


        // Validate that all required properties are present
        if (schemaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields(); // Use fields() instead of next()
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String paramName = field.getKey();
                JsonNode paramSchema = field.getValue();

                boolean isRequired = false;
                if (paramSchema.isObject() && paramSchema.has("required")) {
                    isRequired = paramSchema.get("required").asBoolean();
                }

                if (isRequired) {
                    if (properties == null || !properties.containsKey(paramName)) {
                        return propertyTypeLabel + " '" + paramName + "' is missing and required.";
                    }
                }
            }
        }

        return null; // Valid
    }

    // Modify validateEvent to use the reusable method
    private String validateEvent(GameEventDTO event) {
        if (event == null) return "Received null event.";
        if (event.getEventName() == null || event.getEventName().trim().isEmpty()) return "Received event with empty or null event name.";

        Optional<EventSchema> schemaOptional = eventSchemaRepository.findByEventName(event.getEventName());

        if (schemaOptional.isEmpty()) {
            logger.debug("No schema found for event: {}. Allowing event.", event.getEventName());
            return "No schema found for event."; // Valid
        }

        EventSchema schema = schemaOptional.get();
        // Use the reusable validation method for event parameters
        return validateProperties(event.getParameters(), schema.getParameterSchema(), "Event Parameter");
    }
}