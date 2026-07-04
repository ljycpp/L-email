package com.practice.mailsystem.user.controller;

import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.user.dto.LoginRequest;
import com.practice.mailsystem.user.dto.ProfileUpdateRequest;
import com.practice.mailsystem.user.dto.RegisterRequest;
import com.practice.mailsystem.user.service.UserService;
import com.practice.mailsystem.user.vo.LoginResponse;
import com.practice.mailsystem.user.vo.UserInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证与个人信息 REST API
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/api/auth/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.ok();
    }

    @PostMapping("/api/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @GetMapping("/api/users/me")
    public ApiResponse<UserInfoResponse> me() {
        return ApiResponse.ok(userService.getUserInfo(UserContext.get()));
    }

    @PutMapping("/api/users/me")
    public ApiResponse<UserInfoResponse> updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(userService.updateProfile(UserContext.get(), request));
    }
}
