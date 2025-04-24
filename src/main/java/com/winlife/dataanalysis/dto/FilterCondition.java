package com.winlife.dataanalysis.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterCondition {
    // Attribute path, e.g., "parameter.level", "userProperty.vip_status"
    private String attribute;
    // Operator, e.g., "=", "!=", ">", "<", ">=", "<=", "contains", "not contains", "isNull", "isNotNull"
    private String operator;
    // Filter value (use Object to handle different types: String, Number, Boolean)
    private Object value;

    // Optional: add attributeType here if frontend sends it, helps backend validation/parsing
    // private String attributeType; // e.g., "string", "integer", "boolean"
}