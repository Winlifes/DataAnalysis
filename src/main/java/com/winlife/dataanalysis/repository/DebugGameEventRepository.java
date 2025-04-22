package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.DebugGameEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface DebugGameEventRepository extends JpaRepository<DebugGameEvent, Long> {
    // Find all debug events, sorted by received timestamp descending
    Page<DebugGameEvent> findAllByOrderByReceivedTimestampDesc(Pageable pageable);

    // Find debug events by device ID, sorted by received timestamp descending
    Page<DebugGameEvent> findByDeviceIdOrderByReceivedTimestampDesc(String deviceId, Pageable pageable);
}