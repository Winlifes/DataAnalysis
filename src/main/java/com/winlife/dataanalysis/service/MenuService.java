package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.model.Dashboard;
import com.winlife.dataanalysis.model.DashboardConfig;
import com.winlife.dataanalysis.model.Folder;
import com.winlife.dataanalysis.repository.DashboardConfigRepository;
import com.winlife.dataanalysis.repository.DashboardRepository;
import com.winlife.dataanalysis.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final FolderRepository folderRepository;
    private final DashboardRepository dashboardRepository;
    private final DashboardConfigRepository dashboardConfigRepository;

    public List<Map<String, Object>> getMenuTree(Long userId) {
        List<Folder> folders = folderRepository.findByUserId(userId);
        List<Folder> projectFolders = folderRepository.findByIcon("section-project");
        for (Folder projectFolder : projectFolders) {
            if (!folders.contains(projectFolder)) {
                folders.add(projectFolder);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();

        for (Folder folder : folders) {
            List<Dashboard> dashboards = dashboardRepository.findByFolderId(folder.getId());

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
        return folderRepository.save(folder);
    }

    public Dashboard createDashboard(Long folderId, String title) {
        Dashboard dashboard = new Dashboard(null, folderId, title, LocalDateTime.now(), LocalDateTime.now());
        return dashboardRepository.save(dashboard);
    }

    public void renameFolder(Long id, String newTitle) {
        Folder folder = folderRepository.findById(id).orElseThrow();
        folder.setTitle(newTitle);
        folderRepository.save(folder);
    }

    public void renameDashboard(Long id, String newTitle) {
        Dashboard dashboard = dashboardRepository.findById(id).orElseThrow();
        dashboard.setTitle(newTitle);
        dashboardRepository.save(dashboard);
    }

    public void deleteFolder(Long id) {
        folderRepository.deleteById(id);
    }

    public void deleteDashboard(Long id) {
        dashboardRepository.deleteById(id);
    }

    public void moveDashboard(Long id, Long targetFolderId) {
        Dashboard dashboard = dashboardRepository.findById(id).orElseThrow();
        dashboard.setFolderId(targetFolderId);
        dashboardRepository.save(dashboard);
    }

    public Dashboard getDashboardById(Long id) {
        return dashboardRepository.findById(id).orElseThrow();
    }

    public List<Dashboard> getDashboardsByUserId(Long userId) {
        List<Folder> folders = new ArrayList<>(folderRepository.findByUserId(userId));
        List<Folder> projectFolders = folderRepository.findByIcon("section-project");
        for (Folder projectFolder : projectFolders) {
            if (!folders.contains(projectFolder)) {
                folders.add(projectFolder);
            }
        }
        return dashboardRepository.findByFolderIdIn(folders.stream().map(Folder::getId).collect(Collectors.toList()));
    }

    public void createDashboardConfig(Long id, String title, String config) {
        DashboardConfig dashboardConfig = new DashboardConfig(null, title, "small", "bar", id, config, LocalDateTime.now(), LocalDateTime.now());
        dashboardConfigRepository.save(dashboardConfig);
    }

    public List<DashboardConfig> getDashboardConfigByDashboardId(Long id)
    {
        return dashboardConfigRepository.getDashboardConfigsByDashboardId(id);
    }

    public void deleteDashboardConfig(Long id) {
        dashboardConfigRepository.deleteById(id);
    }

    public void setDashboardConfig(Long id, String chartType, String size) {
        DashboardConfig dashboardConfig = dashboardConfigRepository.findById(id).orElseThrow();
        dashboardConfig.setChartType(chartType);
        dashboardConfig.setSize(size);
        dashboardConfigRepository.save(dashboardConfig);
    }
}
