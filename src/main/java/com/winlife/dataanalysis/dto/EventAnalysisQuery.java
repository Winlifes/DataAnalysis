package com.winlife.dataanalysis.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventAnalysisQuery {
    private Long startTime; // Milliseconds timestamp
    private Long endTime; // Milliseconds timestamp
    private String eventName; // Single selected event name
    private List<String> calculationAttributes; // e.g., ["eventCount", "parameter.level", "userProperty.vip_status"]
    private String groupingAttribute; // e.g., "time.day", "parameter.level", "userProperty.vip_status"
    private List<FilterCondition> globalFilters; // List of filter rules
    // Optional: add chartType here if backend needs to know for result formatting
    // private String chartType; // e.g., "bar", "line", "table"
}