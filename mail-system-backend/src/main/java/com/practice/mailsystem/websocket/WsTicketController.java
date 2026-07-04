package com.practice.mailsystem.websocket;

import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ws")
@RequiredArgsConstructor
public class WsTicketController {

    private final WsTicketService wsTicketService;

    @GetMapping("/ticket")
    public ApiResponse<String> issueTicket() {
        return ApiResponse.ok(wsTicketService.issue(UserContext.get()));
    }
}
