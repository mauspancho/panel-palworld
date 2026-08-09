package com.palworldadmin.app.service.worldstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorldStatsDocument(
        int schemaVersion,
        String generatedAt,
        String saveLastModified,
        int totalPlayers,
        int playersWithBase,
        int totalGuilds,
        List<GuildStats> guilds
) {
    public WorldStatsDocument {
        guilds = guilds == null ? List.of() : List.copyOf(guilds);
    }
}
