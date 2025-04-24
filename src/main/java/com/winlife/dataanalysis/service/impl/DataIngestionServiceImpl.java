package com.winlife.dataanalysis.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winlife.dataanalysis.dto.EventAnalysisQuery;
import com.winlife.dataanalysis.dto.EventReportStatistic;
import com.winlife.dataanalysis.dto.FilterCondition;
import com.winlife.dataanalysis.dto.GameEventDTO;
import com.winlife.dataanalysis.model.*;
import com.winlife.dataanalysis.repository.*;
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;


import jakarta.persistence.EntityManager;
import jakarta.persistence.Query; // Use JPA Query

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataIngestionServiceImpl implements DataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionServiceImpl.class);
    private final GameEventRepository gameEventRepository;
    private final EventSchemaRepository eventSchemaRepository;
    private final ErroredGameEventRepository erroredGameEventRepository;
    private final DebugGameEventRepository debugGameEventRepository;
    private final UserPropertySchemaRepository userPropertySchemaRepository; // Inject UserPropertySchemaRepository
    private final PlayerDataRepository playerDataRepository; // Inject the new repository
    private final EntityManager entityManager; // Inject EntityManager
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
            // Update PlayerData table after validation (regardless of event validity or debug mode)
            // We want to save the latest user properties received.
            if (event.getUserId() != null && !event.getUserId().trim().isEmpty()) {
                updatePlayerData(event.getUserId(), event.getDeviceId(), event.getUserProperties());
            } else {
                logger.warn("Received event with empty or null userId. Cannot update PlayerData.");
            }
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
     * Finds or creates PlayerData and updates user properties.
     *
     * @param userId         The user ID.
     * @param deviceId       The device ID.
     * @param userProperties The latest user properties.
     */
    private void updatePlayerData(String userId, String deviceId, Map<String, Object> userProperties) {
        try {
            Optional<PlayerData> playerDataOptional = playerDataRepository.findById(userId);
            PlayerData playerData;

            if (playerDataOptional.isPresent()) {
                playerData = playerDataOptional.get();
                logger.debug("Found existing PlayerData for userId: {}", userId);
            } else {
                playerData = new PlayerData();
                playerData.setUserId(userId); // Set userId for new record
                logger.debug("Creating new PlayerData for userId: {}", userId);
            }

            // Always update deviceId and userProperties with the latest received data
            playerData.setDeviceId(deviceId);
            try {
                // Serialize the validated user properties map
                playerData.setUserProperties(objectMapper.writeValueAsString(userProperties));
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize user properties for PlayerData update for userId {}: {}", userId, userProperties, e);
                // Decide how to handle this: maybe don't update userProperties field, or set to null
                // For now, we'll log the error and proceed without updating userProperties for this record
                // playerData.setUserProperties(null); // Optional: set to null on serialization failure
            }
            playerData.setLastUpdatedTimestamp(Instant.now().toEpochMilli()); // Set last updated timestamp

            playerDataRepository.save(playerData);
            logger.debug("Successfully updated/created PlayerData for userId: {}", userId);

        } catch (Exception e) {
            logger.error("Failed to update/create PlayerData for userId {}: {}", userId, e.getMessage(), e);
            logger.error("PlayerData update details:", e);
        }
    }

    /**
     * Saves an errored game event to the error table.
     *
     * @param event       The original GameEventDTO.
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
     *
     * @param event           The original GameEventDTO.
     * @param isValid         Whether the event and its user properties were valid according to schemas.
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
     *
     * @param properties        The map of properties (event parameters or user properties).
     * @param schemaJson        The JSON string defining the schema.
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
        if (event.getEventName() == null || event.getEventName().trim().isEmpty())
            return "Received event with empty or null event name.";

        Optional<EventSchema> schemaOptional = eventSchemaRepository.findByEventName(event.getEventName());

        if (schemaOptional.isEmpty()) {
            logger.debug("No schema found for event: {}. Allowing event.", event.getEventName());
            return "No schema found for event."; // Valid
        }

        EventSchema schema = schemaOptional.get();
        // Use the reusable validation method for event parameters
        return validateProperties(event.getParameters(), schema.getParameterSchema(), "Event Parameter");
    }

    @Override
    public List<EventReportStatistic> getEventReportingStatistics(long startTime, long endTime) {
        logger.debug("Fetching event reporting statistics from {} to {}", startTime, endTime);
        // Call the new repository method
        return gameEventRepository.countEventsByEventNameAndTimeRange(startTime, endTime);
    }

    @Override
    public List<PlayerData> getPlayerDataByUserId(String userId) {
        return playerDataRepository.findByUserId(userId);
    }

    @Override
    public List<PlayerData> getPlayerDataByDeviceId(String deviceId) {
        return playerDataRepository.findByDeviceId(deviceId);
    }

    public List<PlayerData> getPlayerDataByUserProperty(String key, String value) {
        // Example using a native query for MySQL (requires specific syntax)
        // This is complex and database-dependent.
        String sql = "SELECT * FROM player_data WHERE JSON_EXTRACT(user_properties, ?) = ?";
        // You would need to use EntityManager or JdbcTemplate to execute this native query
        // and map the results back to PlayerData entities or a DTO.

        // Example using a repository method (requires JPA support for JSON queries or custom implementation)
        // This kind of method signature is NOT standard JPA, it requires custom handling.
        // return playerDataRepository.findByUserProperty(key, value); // This method doesn't exist in standard JpaRepository

        // A simpler, but less efficient approach for small datasets:
        // Fetch all players and filter in memory (NOT recommended for large tables)

        List<PlayerData> allPlayers = playerDataRepository.findAll();
        return allPlayers.stream()
                .filter(player -> {
                    try {
                        JsonNode propertiesNode = objectMapper.readTree(player.getUserProperties());
                        if (propertiesNode != null && propertiesNode.has(key)) {
                            JsonNode valueNode = propertiesNode.get(key);
                            // Compare based on the expected type of the value
                            // Simple string comparison for demonstration
                            return valueNode.asText().equals(value);
                        }
                        return false;
                    } catch (JsonProcessingException e) {
                        logger.error("Failed to parse user properties for filtering userId: {}", player.getUserId(), e);
                        return false;
                    }
                })
                .collect(Collectors.toList());


        // You will need to implement proper database querying for JSON fields here.
        // Consult your database documentation (MySQL, PostgreSQL, etc.) for JSON query functions.
        //throw new UnsupportedOperationException("User property search is not yet implemented in backend."); // Placeholder
    }

    @Override
    public List<EventReportStatistic> getUserEventStatistics(String userId, long startTime, long endTime) {
        logger.debug("Fetching event statistics for userId: {} from {} to {}", userId, startTime, endTime);
        // Requires a custom query in GameEventRepository
        // Example: In GameEventRepository, add:
        // @Query("SELECT new com.winlife.dataanalysis.dto.EventReportStatistic(ge.eventName, COUNT(ge)) " +
        //        "FROM GameEvent ge " +
        //        "WHERE ge.userId = :userId AND ge.timestamp >= :startTime AND ge.timestamp <= :endTime " +
        //        "GROUP BY ge.eventName " +
        //        "ORDER BY COUNT(ge) DESC")
        // List<EventReportStatistic> countEventsByUserIdAndTimeRange(@Param("userId") String userId, @Param("startTime") long startTime, @Param("endTime") long endTime);
        return gameEventRepository.countEventsByUserIdAndTimeRange(userId, startTime, endTime); // Requires this method in Repository
    }

    @Override
    public Page<GameEvent> getUserEventSequence(String userId, int page, int size) {
        logger.debug("Fetching event sequence for userId: {} page {} size {}", userId, page, size);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        // Requires a method in GameEventRepository to find by userId with pagination and sorting
        // Example: In GameEventRepository, add:
        // Page<GameEvent> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
        return gameEventRepository.findByUserIdOrderByTimestampDesc(userId, pageRequest); // Requires this method in Repository
    }

    @Override
    public Optional<EventSchema> getEventSchemaByName(String eventName) {
        logger.debug("Fetching EventSchema for event name: {}", eventName);
        // Use the new repository method
        return eventSchemaRepository.findByEventName(eventName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> runEventAnalysis(EventAnalysisQuery query) {
        logger.info("Running event analysis query...");
        logger.debug("Query details: {}", query);

        // --- Step 1: Validate and Parse the Query DTO ---
        // Perform detailed validation as before. Ensure attributes/operators are valid.
        if (!StringUtils.hasText(query.getEventName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event name is required.");
        }
        if (query.getStartTime() == null || query.getEndTime() == null || query.getStartTime() > query.getEndTime()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid time range is required.");
        }
        if (!StringUtils.hasText(query.getGroupingAttribute())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grouping attribute is required.");
        }
        if (query.getCalculationAttributes() == null || query.getCalculationAttributes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少选择一个计算属性。");
        }

        // Fetch EventSchema to validate attribute paths and types
        Optional<EventSchema> eventSchemaOptional = getEventSchemaByName(query.getEventName());
        if (!eventSchemaOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event schema not found for event " + query.getEventName());
        }
        EventSchema eventSchema = eventSchemaOptional.get();
        // Use eventSchema and userPropertySchema to validate query attributes and types (Implementation needed)


        // --- Step 2: Translate Query Definition into MySQL SQL ---

        StringBuilder sqlBuilder = new StringBuilder();
        List<String> selectExpressions = new ArrayList<>();
        List<String> selectAliases = new ArrayList<>(); // To map results later
        Map<String, Object> parameters = new HashMap<>(); // Use a map for named parameters

        // 2.1 SELECT clause: Include the grouping attribute and calculated attributes.

        // Add grouping column
        String groupingSqlExpression = null;
        String groupingAlias = null; // Alias determined by the attribute

        String groupingAttribute = query.getGroupingAttribute();

        if (groupingAttribute.startsWith("time.")) {
            String timeUnit = groupingAttribute.substring("time.".length());
            // MySQL functions for time formatting/truncation
            if ("day".equals(timeUnit)) {
                groupingSqlExpression = "DATE(FROM_UNIXTIME(ge.timestamp / 1000))"; // Format as YYYY-MM-DD
            } else if ("week".equals(timeUnit)) {
                groupingSqlExpression = "DATE_FORMAT(FROM_UNIXTIME(ge.timestamp / 1000), '%X-%V')"; // Year-Week format
            } else if ("month".equals(timeUnit)) {
                groupingSqlExpression = "DATE_FORMAT(FROM_UNIXTIME(ge.timestamp / 1000), '%Y-%m')"; // Year-Month format
            } else if ("hour".equals(timeUnit)) { // Add hour granularity
                groupingSqlExpression = "DATE_FORMAT(FROM_UNIXTIME(ge.timestamp / 1000), '%Y-%m-%d %H:00')"; // Year-Month-Day Hour:00 format
            }
            // Add other granularities if needed

            // Cast to CHAR to ensure consistent string representation for grouping and ordering
            if (groupingSqlExpression != null) {
                groupingSqlExpression = "CAST(" + groupingSqlExpression + " AS CHAR)";
                groupingAlias = groupingAttribute.replace('.', '_'); // e.g., time_day
            }

        } else if (groupingAttribute.startsWith("parameter.")) {
            String paramName = groupingAttribute.substring("parameter.".length());
            // MySQL JSON extraction function. Using ->> for direct text extraction. JSON_UNQUOTE is safer.
            groupingSqlExpression = "JSON_UNQUOTE(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"'))"; // JSON_UNQUOTE to remove quotes from strings
            groupingAlias = groupingAttribute.replace('.', '_'); // e.g., parameter_level
        } else if (groupingAttribute.startsWith("userProperty.")) {
            String propName = groupingAttribute.substring("userProperty.".length());
            // MySQL JSON extraction function for user properties snapshot
            groupingSqlExpression = "JSON_UNQUOTE(JSON_EXTRACT(ge.user_properties, '$.\"" + propName + "\"'))";
            groupingAlias = groupingAttribute.replace('.', '_'); // e.g., userProperty_vip_status
        } else if (groupingAttribute.equals("userId")) {
            groupingSqlExpression = "ge.user_id";
            groupingAlias = "userId";
        } else if (groupingAttribute.equals("deviceId")) {
            groupingSqlExpression = "ge.device_id";
            groupingAlias = "deviceId";
        } else {
            // Handle other potential grouping attributes (direct columns)
            logger.warn("Unsupported grouping attribute: {}", groupingAttribute);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported grouping attribute: " + groupingAttribute);
        }

        if (groupingSqlExpression != null) {
            selectExpressions.add(groupingSqlExpression + " AS " + groupingAlias);
            selectAliases.add(groupingAlias);
        }


        // Add calculation columns
        boolean needsEventCount = false;
        boolean needsUniqueUserCount = false;

        // Identify if we need eventCount and uniqueUserCount (either explicitly requested or for average calculation)
        if (query.getCalculationAttributes().contains("eventCount")) {
            needsEventCount = true;
        }
        if (query.getCalculationAttributes().contains("uniqueUserCount")) {
            needsUniqueUserCount = true;
        }
        // ** If 'averageCountPerUser' is requested, we need both eventCount and uniqueUserCount **
        if (query.getCalculationAttributes().contains("averageCountPerUser")) {
            needsEventCount = true;
            needsUniqueUserCount = true;
        }


        // Add necessary calculation selects based on identified needs
        if (needsEventCount) {
            // Only add if not already included
            if (!selectAliases.contains("eventCount")) {
                selectExpressions.add("COUNT(*) AS eventCount");
                selectAliases.add("eventCount");
            }
        }
        if (needsUniqueUserCount) {
            // Only add if not already included
            if (!selectAliases.contains("uniqueUserCount")) {
                selectExpressions.add("COUNT(DISTINCT ge.user_id) AS uniqueUserCount");
                selectAliases.add("uniqueUserCount");
            }
        }


        for (String calcAttribute : query.getCalculationAttributes()) {
            // Skip implicit counts as they are handled above
            if (calcAttribute.equals("eventCount") || calcAttribute.equals("uniqueUserCount") || calcAttribute.equals("averageCountPerUser")) {
                continue;
            }

            String calculationSqlExpression = null;
            String calculationAlias = calcAttribute.replace('.', '_');

            if (calcAttribute.startsWith("parameter.")) {
                String[] params = calcAttribute.substring("parameter.".length()).split("@");
                String paramName = params[0];
                String paramType = params.length > 1 ? params[1] : "distinctCount"; // Default to string if no type specified
                // Assuming a default aggregation for parameters, e.g., COUNT(DISTINCT) or SUM for numeric.
                // This still needs schema lookup to determine type and choose SUM/AVG vs COUNT DISTINCT.
                // For now, using COUNT(DISTINCT value) as a generic example.
                // 去重数 distinctCount
                // 总和 sum
                // 均值 avg
                // 最大值 max
                // 最小值 min
                if(paramType.equals("distinctCount")){
                    calculationSqlExpression = "COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"')))";
                }
                else if(paramType.equals("sum")){
                    calculationSqlExpression = "SUM(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"'))";
                }
                else if(paramType.equals("avg")){
                    calculationSqlExpression = "AVG(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"'))";
                }
                else if(paramType.equals("max")){
                    calculationSqlExpression = "MAX(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"'))";
                }
                else if(paramType.equals("min")){
                    calculationSqlExpression = "MIN(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"'))";
                }
                else {
                    logger.warn("Unsupported parameter type: {}", paramType);
                    calculationSqlExpression = "COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"')))";
                }
                calculationAlias = "parameter_" + paramName; // Example alias

            } else if (calcAttribute.startsWith("userProperty.")) {
                String[] params = calcAttribute.substring("userProperty.".length()).split("@");
                String paramName = params[0];
                String paramType = params.length > 1 ? params[1] : "distinctCount";

                // Similar logic for user properties
                // 去重数 distinctCount
                // 总和 sum
                // 均值 avg
                // 最大值 max
                // 最小值 min
                if(paramType.equals("distinctCount")){
                    calculationSqlExpression = "COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(ge.user_properties, '$.\"" + paramName + "\"')))";
                }
                else if(paramType.equals("sum")){
                    calculationSqlExpression = "SUM(JSON_EXTRACT(ge.user_properties, '$.\"" + paramName + "\"'))";
                }
                else if(paramType.equals("avg")){
                    calculationSqlExpression = "AVG(JSON_EXTRACT(ge.user_properties, '$.\"" + paramName + "\"'))";
                }
                else if(paramType.equals("max")){
                    calculationSqlExpression = "MAX(JSON_EXTRACT(ge.user_properties, '$.\"" + paramName + "\"'))";
                }
                else if(paramType.equals("min")){
                    calculationSqlExpression = "MIN(JSON_EXTRACT(ge.user_properties, '$.\"" + paramName + "\"'))";
                }
                else {
                    logger.warn("Unsupported userProperty type: {}", paramType);
                    calculationSqlExpression = "COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(ge.user_properties, '$.\"" + paramName + "\"')))";
                }
                calculationAlias = "userProperty_" + paramName; // Example alias
            } else if (calcAttribute.equals("deviceId")) {
                calculationSqlExpression = "COUNT(DISTINCT ge.device_id)";
                calculationAlias = "uniqueDeviceCount";
            }
            // Handle other potential calculations based on direct columns (e.g., SUM(ge.duration))

            if (calculationSqlExpression != null) {
                // Avoid adding duplicates if an attribute is somehow requested twice
                if (!selectAliases.contains(calculationAlias)) {
                    selectExpressions.add(calculationSqlExpression + " AS " + calculationAlias);
                    selectAliases.add(calculationAlias);
                }
            } else {
                logger.warn("Unsupported calculation attribute: {}", calcAttribute);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported calculation attribute: " + calcAttribute);
            }
        }

        if (selectExpressions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid selection attributes or calculations.");
        }

        sqlBuilder.append("SELECT ").append(String.join(", ", selectExpressions));


        // 2.2 FROM clause
        sqlBuilder.append(" FROM game_events ge");
        // Add joins if necessary (e.g., if querying latest user properties from PlayerData)


        // 2.3 WHERE clause: Filter by time range, event name, and global filters.
        List<String> whereConditions = new ArrayList<>();
        whereConditions.add("ge.user_id IS NOT NULL");
        // Compare timestamp (milliseconds epoch) directly assuming DB column is numeric or compatible
        whereConditions.add("ge.timestamp >= :startTime");
        whereConditions.add("ge.timestamp <= :endTime");

        // Filter by selected event name
        whereConditions.add("ge.event_name = :eventName");

        // Add parameters for time range and event name
        parameters.put("startTime", query.getStartTime());
        parameters.put("endTime", query.getEndTime());
        parameters.put("eventName", query.getEventName());


        // Add global filters
        int filterParamIndex = 0;
        for (FilterCondition filter : query.getGlobalFilters()) {
            String filterAttributeSqlExpression = null;
            String attributePath = filter.getAttribute();
            String operator = filter.getOperator();
            Object filterValue = filter.getValue();
            // String attributeType = "unknown"; // Placeholder: Get type from schema

            if (attributePath.startsWith("parameter.")) {
                String paramName = attributePath.substring("parameter.".length());
                filterAttributeSqlExpression = "JSON_UNQUOTE(JSON_EXTRACT(ge.parameters, '$.\"" + paramName + "\"'))";
                // attributeType = getTypeFromSchema(eventSchema.getParameterSchema(), paramName, 'parameter');
            } else if (attributePath.startsWith("userProperty.")) {
                String propName = attributePath.substring("userProperty.".length());
                filterAttributeSqlExpression = "JSON_UNQUOTE(JSON_EXTRACT(ge.user_properties, '$.\"" + propName + "\"'))";
                // attributeType = getTypeFromSchema(userPropertySchema.getPropertySchema(), propName, 'userProperty'); // Need global user property schema here
            } else if (attributePath.equals("userId")) {
                filterAttributeSqlExpression = "ge.user_id";
                // attributeType = "string";
            } else if (attributePath.equals("deviceId")) {
                filterAttributeSqlExpression = "ge.device_id";
                // attributeType = "string";
            }
            // Handle other direct columns


            if (filterAttributeSqlExpression == null) {
                logger.warn("Could not determine SQL expression for filter attribute: {}", attributePath);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported filter attribute: " + attributePath);
            }

            String filterComparisonSql = null;
            // Parameter name for filter value binding
            String paramName = "filterValue" + filterParamIndex++; // Simple unique param name for binding

            // --- Generate Comparison SQL based on operator and assumed type ---
            // NOTE: Proper type handling is essential here. Using schema to get type is recommended.
            // This example makes assumptions or uses string comparisons where type is ambiguous.

            if ("=".equals(operator)) {
                filterComparisonSql = filterAttributeSqlExpression + " = :" + paramName;
                parameters.put(paramName, filterValue);
            } else if ("!=".equals(operator)) {
                filterComparisonSql = filterAttributeSqlExpression + " != :" + paramName;
                parameters.put(paramName, filterValue);
            } else if (">".equals(operator)) {
                // Assuming numeric type, needs casting if JSON extract returns text
                filterComparisonSql = "CAST(" + filterAttributeSqlExpression + " AS DECIMAL) > :" + paramName;
                parameters.put(paramName, filterValue); // Should be a Number
            } else if ("<".equals(operator)) {
                // Assuming numeric type
                filterComparisonSql = "CAST(" + filterAttributeSqlExpression + " AS DECIMAL) < :" + paramName;
                parameters.put(paramName, filterValue); // Should be a Number
            } else if (">=".equals(operator)) {
                // Assuming numeric type
                filterComparisonSql = "CAST(" + filterAttributeSqlExpression + " AS DECIMAL) >= :" + paramName;
                parameters.put(paramName, filterValue); // Should be a Number
            } else if ("<=".equals(operator)) {
                // Assuming numeric type
                filterComparisonSql = "CAST(" + filterAttributeSqlExpression + " AS DECIMAL) <= :" + paramName;
                parameters.put(paramName, filterValue); // Should be a Number
            } else if ("contains".equals(operator)) {
                // Assuming string type, use LIKE
                filterComparisonSql = filterAttributeSqlExpression + " LIKE CONCAT('%', :" + paramName + ", '%')";
                parameters.put(paramName, escapeLikeValue(filterValue)); // Escape value for LIKE
            } else if ("not contains".equals(operator)) {
                // Assuming string type, use NOT LIKE
                filterComparisonSql = filterAttributeSqlExpression + " NOT LIKE CONCAT('%', :" + paramName + ", '%')";
                parameters.put(paramName, escapeLikeValue(filterValue)); // Escape value for LIKE
            } else if ("isNull".equals(operator)) {
                filterComparisonSql = filterAttributeSqlExpression + " IS NULL";
                // No parameter needed
            } else if ("isNotNull".equals(operator)) {
                filterComparisonSql = filterAttributeSqlExpression + " IS NOT NULL";
                // No parameter needed
            } else {
                logger.warn("Unsupported filter operator: {}", operator);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported filter operator: " + operator);
            }

            if (filterComparisonSql != null) {
                whereConditions.add(filterComparisonSql);
            }
        }


        if (!whereConditions.isEmpty()) {
            sqlBuilder.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }


        // 2.4 GROUP BY clause: Group by the grouping attribute expression.
        // Grouping is done by the *expression*, not the alias.
        if (groupingSqlExpression != null) {
            sqlBuilder.append(" GROUP BY ").append(groupingSqlExpression);
        }


        // 2.5 HAVING clause: Optional, for filtering on aggregated results.

        // 2.6 ORDER BY clause: Order by the grouping attribute expression (time period usually)
        if (groupingSqlExpression != null) {
            sqlBuilder.append(" ORDER BY ").append(groupingSqlExpression).append(" ASC");
        } else {
            // If no grouping (e.g., just calculating totals), maybe order by a calculation alias or just omit ORDER BY
            if (!selectAliases.isEmpty()) {
                // Order by the first non-count calculation alias if no grouping
                Optional<String> firstCalcAlias = selectAliases.stream()
                        .filter(alias -> !alias.equals("eventCount") && !alias.equals("uniqueUserCount") && !alias.equals("averageCountPerUser"))
                        .findFirst();
                if(firstCalcAlias.isPresent()) {
                    sqlBuilder.append(" ORDER BY ").append(firstCalcAlias.get()).append(" ASC");
                } else if (selectAliases.contains("eventCount")) {
                    sqlBuilder.append(" ORDER BY eventCount ASC"); // Order by eventCount if only count is selected
                } // No order by if nothing suitable to order by
            }
        }


        // --- Step 3: Execute the Dynamic SQL Query using EntityManager ---
        String finalSql = sqlBuilder.toString();
        logger.info("Generated SQL Query: {}", finalSql); // Log the generated SQL

        Query nativeQuery = entityManager.createNativeQuery(finalSql);

        // --- Step 3.1: Bind Parameters ---
        try {
            parameters.forEach(nativeQuery::setParameter);
        } catch (IllegalArgumentException e) {
            logger.error("Error binding parameters to dynamic SQL query", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error binding query parameters", e);
        }


        List<Object[]> resultListRaw;
        try {
            resultListRaw = nativeQuery.getResultList(); // Execute
        } catch (Exception e) {
            logger.error("Error executing dynamic SQL query: {}", finalSql, e);
            // Catch specific exceptions (e.g., SQLGrammarException) and provide helpful messages
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error executing analysis query", e);
        }


        // --- Step 4: Format Results and Perform Post-Processing Calculations (e.g., Average) ---
        List<Map<String, Object>> formattedResults = new ArrayList<>();

        if (!resultListRaw.isEmpty()) {
            // Ensure the number of aliases matches the number of columns in the raw result
            if (selectAliases.size() != resultListRaw.get(0).length) {
                logger.error("Column count mismatch between select aliases ({}) and raw query results ({})", selectAliases.size(), resultListRaw.get(0).length);
                // This indicates an error in SQL generation or alias tracking
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to map query results due to column mismatch.");
            }

            for (Object[] row : resultListRaw) {
                Map<String, Object> rowMap = new HashMap<>();
                // Map raw result array to map using aliases
                for (int i = 0; i < selectAliases.size(); i++) {
                    rowMap.put(selectAliases.get(i), row[i]);
                }

                // ** Post-process to calculate 'averageCountPerUser' if requested **
                if (query.getCalculationAttributes().contains("averageCountPerUser")) {
                    // Ensure eventCount and uniqueUserCount are present (they should be if average was requested)
                    Number eventCountNum = (Number) rowMap.get("eventCount");
                    Number uniqueUserCountNum = (Number) rowMap.get("uniqueUserCount");

                    double eventCount = (eventCountNum != null) ? eventCountNum.doubleValue() : 0.0;
                    double uniqueUserCount = (uniqueUserCountNum != null) ? uniqueUserCountNum.doubleValue() : 0.0;

                    double averageCountPerUser = (uniqueUserCount > 0) ? eventCount / uniqueUserCount : 0.0;

                    // Add the calculated average to the row map
                    rowMap.put("averageCountPerUser", averageCountPerUser);

                    // Add alias for averageCountPerUser if not already in selectAliases (it won't be from SQL select)
                    if (!selectAliases.contains("averageCountPerUser")) {
                        // This is just for tracking aliases on the backend side for logging/debugging if needed,
                        // the map already has the key.
                    }
                }

                formattedResults.add(rowMap);
            }
        }

        logger.info("Successfully executed dynamic SQL query and formatted results.");
        logger.debug("Formatted results (first 5 rows): {}", formattedResults.stream().limit(5).toList());

        return formattedResults;
    }

    // Helper method to escape values for SQL (example, needs careful implementation)
    private String escapeAndFormatValue(Object value, String type) {
        if (value == null) return "NULL";
        switch (type) {
            case "string":
                // Escape single quotes
                return "'" + value.toString().replace("'", "''") + "'";
            case "integer":
            case "float":
                // Validate it's a number
                if (value instanceof Number) return value.toString();
                if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value) + "";
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Expected number for filter value");
                    }
                }
                throw new IllegalArgumentException("Expected number for filter value");
            case "boolean":
                if (value instanceof Boolean) return ((Boolean) value) ? "TRUE" : "FALSE"; // Or 1/0 depending on DB
                throw new IllegalArgumentException("Expected boolean for filter value");
                // Add cases for other types
            default:
                // Default to string representation, needs careful escaping
                return "'" + value.toString().replace("'", "''") + "'";
        }
    }

    // Helper method to escape values for SQL LIKE (example)
    private String escapeLikeValue(Object value) {
        if (value == null) return "";
        String strValue = value.toString();
        // Escape %, _, and \
        strValue = strValue.replace("\\", "\\\\");
        strValue = strValue.replace("%", "\\%");
        strValue = strValue.replace("_", "\\_");
        return strValue;
    }

    // Example helper to get type from schema (conceptual)
     private String getTypeFromSchema(String schemaJson, String attributeName, String schemaType) {
         if (schemaJson == null) return null;
         try {
             JsonNode schemaNode = objectMapper.readTree(schemaJson);
             JsonNode attributeDef = schemaNode.get(attributeName);
             if (attributeDef == null) return null;

             if (attributeDef.isTextual()) { // Simple type string e.g., "integer"
                 return attributeDef.asText();
             } else if (attributeDef.isObject()) { // Object with "type": "integer"
                 JsonNode typeNode = attributeDef.get("type");
                 if (typeNode != null && typeNode.isTextual()) {
                     return typeNode.asText();
                 }
             }
         } catch (Exception e) {
             logger.error("Failed to parse schema JSON to get attribute type:", e);
         }
         return null; // Type not found or parsing failed
     }

}