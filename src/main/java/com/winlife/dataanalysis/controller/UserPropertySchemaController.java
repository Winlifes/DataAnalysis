package com.winlife.dataanalysis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winlife.dataanalysis.model.UserPropertySchema;
import com.winlife.dataanalysis.repository.UserPropertySchemaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/schemas/user-properties") // Endpoint for user property schema
@RequiredArgsConstructor
public class UserPropertySchemaController {

    private static final Logger logger = LoggerFactory.getLogger(UserPropertySchemaController.class);
    private final UserPropertySchemaRepository userPropertySchemaRepository;
    private final ObjectMapper objectMapper; // Inject ObjectMapper for validation

    /**
     * Get the user property schema.
     * @return The user property schema, or 404 if not found.
     */
    @GetMapping
    public ResponseEntity<UserPropertySchema> getUserPropertySchema() {
        Optional<UserPropertySchema> schema = userPropertySchemaRepository.findFirstByOrderByIdAsc();
        return schema.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create or update the user property schema.
     * Since there's usually only one, we can use PUT or POST,
     * or distinguish based on whether a schema already exists.
     * Let's use POST for creation and PUT for update, or a single PUT for upsert.
     * A single PUT for upserting the *only* schema is common.
     * @param schemaData The user property schema data (JSON string or structured).
     * @return The saved user property schema.
     */
    @PutMapping // Use PUT for upserting the single schema
    public ResponseEntity<UserPropertySchema> upsertUserPropertySchema(@RequestBody UserPropertySchema schemaData) {
        // In a single-schema scenario, we might always use ID 1 or find existing
        Optional<UserPropertySchema> existingSchemaOptional = userPropertySchemaRepository.findFirstByOrderByIdAsc();
        UserPropertySchema schemaToSave;

        if (existingSchemaOptional.isPresent()) {
            schemaToSave = existingSchemaOptional.get();
            logger.debug("Updating existing user property schema with ID: {}", schemaToSave.getId());
        } else {
            schemaToSave = new UserPropertySchema();
            logger.debug("Creating new user property schema.");
        }

        // TODO: Add validation for the propertySchema JSON format
        String validationError = validateSchemaJson(schemaData.getPropertySchema());
        if (validationError != null) {
            logger.warn("Invalid user property schema JSON received: {}", validationError);
            // Return 400 Bad Request with error message
            return ResponseEntity.badRequest().header("X-Error-Reason", validationError).build();
        }


        schemaToSave.setPropertySchema(schemaData.getPropertySchema());

        try {
            UserPropertySchema savedSchema = userPropertySchemaRepository.save(schemaToSave);
            return ResponseEntity.ok(savedSchema); // Return 200 OK
        } catch (Exception e) {
            logger.error("Failed to save user property schema", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Simple validation of the JSON structure for the schema
    private String validateSchemaJson(String schemaJson) {
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            return null; // Empty schema is valid (no properties defined)
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            if (!schemaNode.isObject()) {
                return "Schema must be a JSON object.";
            }
            // Optional: More detailed checks here, e.g., ensuring values are "string", "integer", or objects with "type"
            Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
            while(fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode paramDef = field.getValue();
                if (!paramDef.isTextual() && !paramDef.isObject()) {
                    return "Property definition for '" + field.getKey() + "' must be a string (type) or an object.";
                }
                if (paramDef.isObject() && !paramDef.has("type")) {
                    return "Property definition object for '" + field.getKey() + "' is missing 'type'.";
                }
                // You could add more checks for required: boolean etc.
            }
            return null; // Valid JSON structure
        } catch (JsonProcessingException e) {
            logger.error("Invalid JSON format for schema: {}", schemaJson, e);
            return "Invalid JSON format.";
        }
    }

    // DELETE endpoint might not be necessary if you always upsert,
    // or you could have one to delete the single schema.
    @DeleteMapping("/{id}") // Allow deleting by ID, though maybe only ID 1 is used
    public ResponseEntity<Void> deleteUserPropertySchema(@PathVariable Long id) {
        if (!userPropertySchemaRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userPropertySchemaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}