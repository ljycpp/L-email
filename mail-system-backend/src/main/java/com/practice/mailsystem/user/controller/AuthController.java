package com.practice.mailsystem.user.controller;

import com.practice.mailsystem.auth.LoginUser;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.user.dto.LoginRequest;
import com.practice.mailsystem.user.dto.RegisterRequest;
import com.practice.mailsystem.user.service.UserService;
import com.practice.mailsystem.user.vo.LoginResponse;
import com.practice.mailsystem.user.vo.UserInfoResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

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

    @PostMapping("/login/loginbyemail")
    public LoginResponse legacyLogin(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/login/logout")
    public String legacyLogout() {
        return "success";
    }

    @GetMapping("/user/info")
    public UserInfoResponse legacyUserInfo() {
        LoginUser loginUser = UserContext.get();
        return userService.getUserInfo(loginUser);
    }
}
