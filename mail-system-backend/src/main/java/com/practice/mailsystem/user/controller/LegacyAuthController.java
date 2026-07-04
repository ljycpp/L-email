package com.practice.mailsystem.user.controller;

import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.user.dto.LoginRequest;
import com.practice.mailsystem.user.dto.ProfileUpdateRequest;
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
 * 旧版认证路径，响应格式已统一为 {@link ApiResponse}。
 *
 * @deprecated 请迁移到 {@link AuthController}（/api/auth/**）。
 */
@Deprecated
@RestController
@RequiredArgsConstructor
public class LegacyAuthController {

    private final UserService userService;

    @PostMapping("/login/loginbyemail")
    public ApiResponse<LoginResponse> legacyLogin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @PostMapping("/login/logout")
    public ApiResponse<Void> legacyLogout() {
        return ApiResponse.ok();
    }

    @GetMapping("/user/info")
    public ApiResponse<UserInfoResponse> legacyUserInfo() {
        return ApiResponse.ok(userService.getUserInfo(UserContext.get()));
    }

    @PutMapping("/user/info")
    public ApiResponse<UserInfoResponse> legacyUpdateUserInfo(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(userService.updateProfile(UserContext.get(), request));
    }
}
