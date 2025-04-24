package com.winlife.dataanalysis.service.impl;

import com.winlife.dataanalysis.model.MetricDefinition;
import com.winlife.dataanalysis.repository.MetricDefinitionRepository;
import com.winlife.dataanalysis.service.MetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // Import HttpStatus
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException; // Import ResponseStatusException

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MetricDefinitionRepository metricDefinitionRepository;

    @Override
    public List<MetricDefinition> getAllMetrics() {
        return metricDefinitionRepository.findAll();
    }

    @Override
    public Optional<MetricDefinition> getMetricById(Long id) {
        return metricDefinitionRepository.findById(id);
    }

    @Override
    @Transactional // Use Transactional for write operations
    public MetricDefinition createMetric(MetricDefinition metricDefinition) {
        // Optional: Add check for unique name before saving
        // if (metricDefinitionRepository.findByName(metricDefinition.getName()).isPresent()) {
        //     throw new ResponseStatusException(HttpStatus.CONFLICT, "Metric name already exists");
        // }
        // JPA repository save handles both create and update based on ID presence
        return metricDefinitionRepository.save(metricDefinition);
    }

    @Override
    @Transactional // Use Transactional for write operations
    public MetricDefinition updateMetric(Long id, MetricDefinition updatedMetricDefinition) {
        MetricDefinition existingMetric = metricDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric not found with id " + id));

        // Update only the allowed fields
        existingMetric.setDescription(updatedMetricDefinition.getDescription());
        existingMetric.setUnit(updatedMetricDefinition.getUnit());
        existingMetric.setDefinition(updatedMetricDefinition.getDefinition());

        // Name cannot be changed after creation if it's the primary key or unique identifier
        // If name can be changed, add logic here and handle potential unique constraint violations

        return metricDefinitionRepository.save(existingMetric);
    }

    @Override
    @Transactional // Use Transactional for delete operation
    public void deleteMetric(Long id) {
        if (!metricDefinitionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric not found with id " + id);
        }
        metricDefinitionRepository.deleteById(id);
    }

    @Override
    public Optional<MetricDefinition> getMetricByName(String name) {
        // Requires adding 'Optional<MetricDefinition> findByName(String name);' to MetricDefinitionRepository
        // return metricDefinitionRepository.findByName(name);
        throw new UnsupportedOperationException("Finding metric by name is not implemented yet.");
    }
}