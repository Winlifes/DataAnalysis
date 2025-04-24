package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.PlayerData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerDataRepository extends JpaRepository<PlayerData, String> {

    // JPA automatically provides findById (using userId as ID) and save

    // Optional: if you need to find by deviceId, although userId is the primary key
    List<PlayerData> findByUserId(String deviceId);

    List<PlayerData> findByDeviceId(String deviceId);
}