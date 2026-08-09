package com.palworldadmin.app.service.worldstats;

import java.util.List;

public record WorldStatsResult(
        int schemaVersion,
        String generatedAt,
        String saveLastModified,
        int totalPlayers,
        int playersWithBase,
        int totalGuilds,
        List<GuildStats> guilds,
        boolean available,
        String message
) {
    public WorldStatsResult {
        guilds = guilds == null ? List.of() : List.copyOf(guilds);
        message = message == null || message.isBlank() ? "OK" : message;
    }

    public static WorldStatsResult available(WorldStatsDocument document) {
        return new WorldStatsResult(
                document.schemaVersion(),
                document.generatedAt(),
                document.saveLastModified(),
                document.totalPlayers(),
                document.playersWithBase(),
                document.totalGuilds(),
                document.guilds(),
                true,
                "OK"
        );
    }

    public static WorldStatsResult available(int totalPlayers, int playersWithBase, int totalGuilds, List<GuildStats> guilds) {
        return new WorldStatsResult(1, null, null, totalPlayers, playersWithBase, totalGuilds, guilds, true, "OK");
    }

    public static WorldStatsResult unavailable(String message) {
        return new WorldStatsResult(0, null, null, 0, 0, 0, List.of(), false, message);
    }
}
