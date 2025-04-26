package com.winlife.dataanalysis.repository;

import com.winlife.dataanalysis.model.DashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, Long> {

    List<DashboardConfig> getDashboardConfigsByDashboardId(Long id);
}
