package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.model.Dashboard;
import com.winlife.dataanalysis.model.Folder;
import com.winlife.dataanalysis.repository.DashboardRepository;
import com.winlife.dataanalysis.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final FolderRepository folderRepo;
    private final DashboardRepository dashboardRepo;

    public List<Map<String, Object>> getMenuTree(Long userId) {
        List<Folder> folders = folderRepo.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Folder folder : folders) {
            List<Dashboard> dashboards = dashboardRepo.findByFolderId(folder.getId());

            Map<String, Object> folderMap = new HashMap<>();
            folderMap.put("id", folder.getId().toString());
            folderMap.put("title", folder.getTitle());
            folderMap.put("icon", folder.getIcon());
            folderMap.put("children", dashboards.stream().map(db -> {
                Map<String, Object> dbMap = new HashMap<>();
                dbMap.put("id", db.getId().toString());
                dbMap.put("title", db.getTitle());
                return dbMap;
            }).collect(Collectors.toList()));

            result.add(folderMap);
        }

        return result;
    }

    public Folder createFolder(Long userId, String title, String icon) {
        Folder folder = new Folder(null, userId, title, icon, LocalDateTime.now());
        return folderRepo.save(folder);
    }

    public Dashboard createDashboard(Long folderId, String title) {
        Dashboard dashboard = new Dashboard(null, folderId, title, LocalDateTime.now());
        return dashboardRepo.save(dashboard);
    }

    public void renameFolder(Long id, String newTitle) {
        Folder folder = folderRepo.findById(id).orElseThrow();
        folder.setTitle(newTitle);
        folderRepo.save(folder);
    }

    public void renameDashboard(Long id, String newTitle) {
        Dashboard dashboard = dashboardRepo.findById(id).orElseThrow();
        dashboard.setTitle(newTitle);
        dashboardRepo.save(dashboard);
    }

    public void deleteFolder(Long id) {
        folderRepo.deleteById(id);
    }

    public void deleteDashboard(Long id) {
        dashboardRepo.deleteById(id);
    }
}
