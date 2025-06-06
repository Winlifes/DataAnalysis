package com.winlife.dataanalysis.controller;

import com.winlife.dataanalysis.dto.LoginRequest;
import com.winlife.dataanalysis.dto.LoginResponse;
import com.winlife.dataanalysis.model.User;
import com.winlife.dataanalysis.util.JwtUtil;
import com.winlife.dataanalysis.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;  // 这里可以正确注入

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            String token = jwtUtil.generateToken(userDetails);
            Optional<User> optionalUser = userService.findByUsername(request.getUsername());
            String nickname;
            boolean isSuperAdmin, isCheck, isEdit, isExport;
            if (optionalUser.isPresent()) {
                nickname = optionalUser.get().getNickname();
                isSuperAdmin = optionalUser.get().isAuthAdmin();
                isCheck = optionalUser.get().isAuthCheck();
                isEdit = optionalUser.get().isAuthEdit();
                isExport = optionalUser.get().isAuthExport();
            }
            else {
                nickname = "未设置";
                isSuperAdmin = false;
                isCheck = false;
                isEdit = false;
                isExport = false;
            }
            return ResponseEntity.ok(new LoginResponse("success", "登录成功", token, nickname, isSuperAdmin, isExport, isEdit, isCheck));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("fail", "用户名或密码错误", null, null, false, false, false, false));
        }
    }

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return userService.register(user);
    }
}
