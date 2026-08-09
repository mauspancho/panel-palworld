package com.palworldadmin.app.service.worldstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuildMemberStats(
        String uid,
        String name,
        boolean leader
) {
}
