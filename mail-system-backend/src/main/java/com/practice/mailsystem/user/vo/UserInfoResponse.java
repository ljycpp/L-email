package com.practice.mailsystem.user.vo;

import java.util.List;

public record UserInfoResponse(
        List<String> role,
        String name,
        String avatar,
        Long uid,
        String introduction,
        String email
) {
}
