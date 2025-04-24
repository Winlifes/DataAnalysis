package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.dto.EventReportStatistic;
import com.winlife.dataanalysis.model.GameEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameEventRepository extends JpaRepository<GameEvent, Long> {
    // Spring Data JPA provides basic CRUD operations automatically
    // Add this method for reporting statistics
    @Query("SELECT new com.winlife.dataanalysis.dto.EventReportStatistic(ge.eventName, COUNT(ge)) " +
            "FROM GameEvent ge " +
            "WHERE ge.timestamp >= :startTime AND ge.timestamp <= :endTime " +
            "GROUP BY ge.eventName " +
            "ORDER BY COUNT(ge) DESC") // Order by count descending
    List<EventReportStatistic> countEventsByEventNameAndTimeRange(@Param("startTime") long startTime, @Param("endTime") long endTime);

    /**
     * 获取指定用户ID在指定时间范围内的事件上报统计数据
     * (Requires adding userId filter to the query)
     */
    @Query("SELECT new com.winlife.dataanalysis.dto.EventReportStatistic(ge.eventName, COUNT(ge)) " +
            "FROM GameEvent ge " +
            "WHERE ge.userId = :userId AND ge.timestamp >= :startTime AND ge.timestamp <= :endTime " +
            "GROUP BY ge.eventName " +
            "ORDER BY COUNT(ge) DESC")
    List<EventReportStatistic> countEventsByUserIdAndTimeRange(@Param("userId") String userId, @Param("startTime") long startTime, @Param("endTime") long endTime);


    /**
     * 获取指定用户ID的行为序列（分页，按时间倒序）
     * (Standard Spring Data JPA query derivation)
     */
    Page<GameEvent> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
}