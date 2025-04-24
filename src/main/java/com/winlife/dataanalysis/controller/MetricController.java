package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.model.MetricDefinition;
import com.winlife.dataanalysis.service.MetricService; // Import MetricService
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metrics") // Base path for metric endpoints
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService; // Inject MetricService

    /**
     * Get all metric definitions.
     */
    @GetMapping
    public List<MetricDefinition> getAllMetrics() {
        return metricService.getAllMetrics();
    }

    /**
     * Get a specific metric definition by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<MetricDefinition> getMetricById(@PathVariable Long id) {
        return metricService.getMetricById(id)
                .map(ResponseEntity::ok) // If found, return 200 OK with body
                .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found
    }

    /**
     * Create a new metric definition.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // Return 201 Created on success
    public MetricDefinition createMetric(@RequestBody MetricDefinition metricDefinition) {
        // Service layer handles saving and potential unique constraint checks
        return metricService.createMetric(metricDefinition);
    }

    /**
     * Update an existing metric definition.
     */
    @PutMapping("/{id}")
    public MetricDefinition updateMetric(@PathVariable Long id, @RequestBody MetricDefinition metricDefinition) {
        // Service layer handles finding, updating, and error handling (e.g., 404)
        return metricService.updateMetric(id, metricDefinition);
    }

    /**
     * Delete a metric definition by ID.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Return 204 No Content on successful deletion
    public void deleteMetric(@PathVariable Long id) {
        // Service layer handles deletion and error handling (e.g., 404)
        metricService.deleteMetric(id);
    }

    // Optional: Add endpoint to find by name if needed
    // @GetMapping("/by-name/{name}")
    // public ResponseEntity<MetricDefinition> getMetricByName(@PathVariable String name) {
    //     return metricService.getMetricByName(name)
    //             .map(ResponseEntity::ok)
    //             .orElseGet(() -> ResponseEntity.notFound().build());
    // }
}