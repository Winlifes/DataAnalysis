package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.MetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Long> {
    // JpaRepository provides basic CRUD (findAll, findById, save, deleteById)
    // Spring Data JPA can automatically handle checks for unique constraints on save.
}