package com.palworldadmin.app.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RconPlayersView(
        Long serverId,
        String serverName,
        boolean enabled,
        boolean success,
        String message,
        List<RconPlayerView> players,
        String raw,
        LocalDateTime refreshedAt
) {
    public static RconPlayersView disabled(Long serverId, String serverName) {
        return new RconPlayersView(serverId, serverName, false, false, "RCON no esta configurado.", List.of(), "", LocalDateTime.now());
    }

    public static RconPlayersView failed(Long serverId, String serverName, String message) {
        return new RconPlayersView(serverId, serverName, true, false, message, List.of(), "", LocalDateTime.now());
    }
}
