package com.practice.mailsystem.websocket;

import com.practice.mailsystem.auth.LoginUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性 WebSocket 握手票据，避免在 URL 中传递 JWT。
 */
@Service
public class WsTicketService {

    private static final long TTL_SECONDS = 60;

    private final Map<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    public String issue(LoginUser user) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new TicketEntry(user.userId(), user.email(), Instant.now().plusSeconds(TTL_SECONDS)));
        purgeExpired();
        return ticket;
    }

    public LoginUser consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        TicketEntry entry = tickets.remove(ticket.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return new LoginUser(entry.userId(), entry.email());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private record TicketEntry(Long userId, String email, Instant expiresAt) {
    }
}
