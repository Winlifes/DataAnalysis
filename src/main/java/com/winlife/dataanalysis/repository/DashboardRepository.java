package com.winlife.dataanalysis.repository;


import com.winlife.dataanalysis.model.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {
    List<Dashboard> findByFolderId(Long folderId);
    void deleteByFolderId(Long folderId); // 级联删除辅助（如果没开启数据库级联）

    List<Dashboard> findByFolderIdIn(List<Long> collect);

    Dashboard getDashboardById(Long id);
}