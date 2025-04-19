// src/main/java/com/winlife/dataanalysis/repository/ErroredGameEventRepository.java
package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.ErroredGameEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ErroredGameEventRepository extends JpaRepository<ErroredGameEvent, Long> {
    // Spring Data JPA 会根据方法名自动生成查询，按 receivedTimestamp 降序排序
    Page<ErroredGameEvent> findAllByOrderByReceivedTimestampDesc(Pageable pageable);
}