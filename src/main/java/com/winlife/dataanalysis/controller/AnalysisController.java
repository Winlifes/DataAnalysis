package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.EventAnalysisQuery;
import com.winlife.dataanalysis.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectionController.class);

    private final DataIngestionService dataIngestionService;
    /**
     * Executes an event analysis query based on the provided definition.
     * @param query The analysis query definition.
     * @return The analysis results (structure depends on the query).
     */
    @PostMapping("/event") // New endpoint path
    public List<Map<String, Object>> runEventAnalysis(@RequestBody EventAnalysisQuery query) { // Return dynamic structure
        logger.info("Received event analysis query: {}", query);
        // Service layer handles the complex parsing, SQL generation, and execution
        return dataIngestionService.runEventAnalysis(query); // Call the new service method
    }

}
