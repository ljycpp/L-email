package com.practice.mailsystem.priority.controller;

import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.priority.dto.PriorityAutoFilterStatusVO;
import com.practice.mailsystem.priority.service.MailPriorityAutoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MailPriorityController {

    private final MailPriorityAutoService mailPriorityAutoService;

    public MailPriorityController(MailPriorityAutoService mailPriorityAutoService) {
        this.mailPriorityAutoService = mailPriorityAutoService;
    }

    @GetMapping("/mail/priority/auto-filter/status")
    public ApiResponse<PriorityAutoFilterStatusVO> status() {
        return ApiResponse.ok(mailPriorityAutoService.getStatus());
    }

    @PostMapping("/mail/priority/auto-filter/enable")
    public ApiResponse<Void> enable() {
        mailPriorityAutoService.enable();
        return ApiResponse.ok();
    }

    @PostMapping("/mail/priority/auto-filter/disable")
    public ApiResponse<Void> disable() {
        mailPriorityAutoService.disable();
        return ApiResponse.ok();
    }
}
