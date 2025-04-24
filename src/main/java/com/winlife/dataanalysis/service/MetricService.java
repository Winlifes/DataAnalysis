package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.model.MetricDefinition;

import java.util.List;
import java.util.Optional;

public interface MetricService {

    List<MetricDefinition> getAllMetrics();

    Optional<MetricDefinition> getMetricById(Long id);

    MetricDefinition createMetric(MetricDefinition metricDefinition);

    MetricDefinition updateMetric(Long id, MetricDefinition updatedMetricDefinition);

    void deleteMetric(Long id);

    // Optional: Method to find metric by name if needed
    Optional<MetricDefinition> getMetricByName(String name);
}