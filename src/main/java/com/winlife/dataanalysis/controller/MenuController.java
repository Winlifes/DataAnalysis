package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.ConfigDTO;
import com.winlife.dataanalysis.model.Dashboard;
import com.winlife.dataanalysis.model.DashboardConfig;
import com.winlife.dataanalysis.model.Folder;
import com.winlife.dataanalysis.model.User;
import com.winlife.dataanalysis.repository.UserRepository;
import com.winlife.dataanalysis.service.MenuService;
import com.winlife.dataanalysis.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final UserRepository userRepository;

    @GetMapping("/list")
    public List<Map<String, Object>> getMenuTree() {
        String username = JwtUtil.getUsername();
        User user = userRepository.findByUsername(username).orElseThrow();
        return menuService.getMenuTree(user.getId());
    }

    @PostMapping("/folder")
    public Folder createFolder(@RequestBody Map<String, String> req) {
        String username = JwtUtil.getUsername();
        User user = userRepository.findByUsername(username).orElseThrow();
        return menuService.createFolder(user.getId(), req.get("title"), req.get("parentId")); // Expecting parentId
    }

    @PostMapping("/dashboard")
    public Dashboard createDashboard(@RequestBody Map<String, String> req) {
        Long folderId = Long.parseLong(req.get("folderId"));
        return menuService.createDashboard(folderId, req.get("title"));
    }

    @PutMapping("/folder")
    public void renameFolder(@RequestBody Map<String, String> req) {
        menuService.renameFolder(Long.parseLong(req.get("id")), req.get("title"));
    }

    @PutMapping("/dashboard")
    public void renameDashboard(@RequestBody Map<String, String> req) {
        menuService.renameDashboard(Long.parseLong(req.get("id")), req.get("title"));
    }

    @DeleteMapping("/folder/{id}")
    public void deleteFolder(@PathVariable Long id) {
        menuService.deleteFolder(id);
    }

    @DeleteMapping("/dashboard/{id}")
    public void deleteDashboard(@PathVariable Long id) {
        menuService.deleteDashboard(id);
    }

    @PutMapping("/dashboard/move")
    public void moveDashboard(@RequestBody Map<String, String> req) {
        menuService.moveDashboard(Long.parseLong(req.get("id")), Long.parseLong(req.get("targetFolderId")));
    }

    @GetMapping("/dashboard")
    public List<Dashboard> getDashboardsByUserId() {
        String username = JwtUtil.getUsername();
        User user = userRepository.findByUsername(username).orElseThrow();
        return menuService.getDashboardsByUserId(user.getId());
    }

    @GetMapping("/dashboard/{id}")
    public Dashboard getDashboardById(@PathVariable Long id) {
        return menuService.getDashboardById(id);
    }

    @PostMapping("/dashboard/config")
    public void createDashboardConfig(@RequestBody ConfigDTO req) {
        Long dashboardId = req.dashboardId;
        menuService.createDashboardConfig(dashboardId, req.getTitle(), req.config);
    }

    @GetMapping("/dashboard/config/{id}")
    public List<DashboardConfig> getDashboardConfigByDashboardId(@PathVariable Long id)
    {
        return menuService.getDashboardConfigByDashboardId(id);
    }

    @DeleteMapping("/dashboard/config/{id}")
    public void deleteDashboardConfig(@PathVariable Long id)
    {
        menuService.deleteDashboardConfig(id);
    }

    @PutMapping("/dashboard/config")
    public void setDashboardConfig(@RequestBody Map<String, String> req)
    {
        menuService.setDashboardConfig(Long.parseLong(req.get("id")), req.get("chartType"), req.get("size"));
    }
}