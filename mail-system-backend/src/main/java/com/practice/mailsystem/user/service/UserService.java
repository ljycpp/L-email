package com.practice.mailsystem.user.service;

import com.practice.mailsystem.auth.LoginUser;
import com.practice.mailsystem.user.dto.LoginRequest;
import com.practice.mailsystem.user.dto.ProfileUpdateRequest;
import com.practice.mailsystem.user.dto.RegisterRequest;
import com.practice.mailsystem.user.vo.LoginResponse;
import com.practice.mailsystem.user.vo.UserInfoResponse;

public interface UserService {

    LoginResponse login(LoginRequest request);

    void register(RegisterRequest request);

    UserInfoResponse getUserInfo(LoginUser loginUser);

    UserInfoResponse updateProfile(LoginUser loginUser, ProfileUpdateRequest request);
}
