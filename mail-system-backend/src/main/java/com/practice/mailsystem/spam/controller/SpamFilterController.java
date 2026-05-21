package com.practice.mailsystem.spam.controller;

import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.spam.dto.SpamAutoFilterStatusVO;
import com.practice.mailsystem.spam.service.SpamAutoFilterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpamFilterController {

    private final SpamAutoFilterService spamAutoFilterService;

    public SpamFilterController(SpamAutoFilterService spamAutoFilterService) {
        this.spamAutoFilterService = spamAutoFilterService;
    }

    @GetMapping("/mail/spam/auto-filter/status")
    public ApiResponse<SpamAutoFilterStatusVO> status() {
        return ApiResponse.ok(spamAutoFilterService.getStatus());
    }

    @PostMapping("/mail/spam/auto-filter/enable")
    public ApiResponse<Void> enable() {
        spamAutoFilterService.enable();
        return ApiResponse.ok();
    }

    @PostMapping("/mail/spam/auto-filter/disable")
    public ApiResponse<Void> disable() {
        spamAutoFilterService.disable();
        return ApiResponse.ok();
    }
}
