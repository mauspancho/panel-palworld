package com.palworldadmin.app.service.worldstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuildStats(
        String id,
        String name,
        GuildPlayerRef leader,
        int bases,
        int memberCount,
        List<GuildMemberStats> members
) {
    public GuildStats {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
