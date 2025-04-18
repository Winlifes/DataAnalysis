// src/main/java/com/winlife/dataanalysis/repository/EventSchemaRepository.java
package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.EventSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventSchemaRepository extends JpaRepository<EventSchema, Long> {
    // 根据事件名称查找事件结构
    Optional<EventSchema> findByEventName(String eventName);
}