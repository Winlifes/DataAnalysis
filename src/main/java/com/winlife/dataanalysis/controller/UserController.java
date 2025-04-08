package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.BindContactRequest;
import com.winlife.dataanalysis.dto.PasswordChangeRequest;
import com.winlife.dataanalysis.dto.UserInfoResponse;
import com.winlife.dataanalysis.dto.UserPermissionResponse;
import com.winlife.dataanalysis.model.User;
import com.winlife.dataanalysis.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody PasswordChangeRequest request, Principal principal) {
        String username = principal.getName();
        boolean success = userService.changePassword(username, request.getOldPassword(), request.getNewPassword());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "旧密码错误"));
        }
    }

    @PostMapping("/bind-contact")
    public ResponseEntity<?> bindContact(@RequestBody BindContactRequest request, Principal principal) {
        String username = principal.getName();
        boolean success = userService.bindContact(username, request.getType(), request.getValue());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "绑定成功"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "绑定失败"));
        }
    }

    @GetMapping("/user-info")
    public ResponseEntity<UserInfoResponse> getUserInfo(Principal principal) {
        String username = principal.getName();
        Optional<User> user = userService.findByUsername(username);
        return user.map(value -> ResponseEntity.ok(new UserInfoResponse(value.getUsername(), value.getNickname(), value.getPhone(), value.getEmail()))).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    @GetMapping("/permissions")
    public ResponseEntity<UserPermissionResponse> getUserPermission(Principal principal) {
        String username = principal.getName();
        Optional<User> user = userService.findByUsername(username);
        if (user.isPresent()) {
            UserPermissionResponse.UserPermission[] userPermissions = new UserPermissionResponse.UserPermission[4];
            userPermissions[0] = new UserPermissionResponse.UserPermission("查看数据", "可以查看分析数据", user.get().isAuthCheck() ? "启用" : "禁用");
            userPermissions[1] = new UserPermissionResponse.UserPermission("编辑看板", "可以创建/编辑数据看板", user.get().isAuthEdit() ? "启用" : "禁用");
            userPermissions[2] = new UserPermissionResponse.UserPermission("数据导出", "可以导出分析数据", user.get().isAuthExport() ? "启用" : "禁用");
            userPermissions[3] = new UserPermissionResponse.UserPermission("系统管理", "后台系统权限", user.get().isAuthAdmin() ? "启用" : "禁用");
            return ResponseEntity.ok(new UserPermissionResponse(userPermissions));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
}
