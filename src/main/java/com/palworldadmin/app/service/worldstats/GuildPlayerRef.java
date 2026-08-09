package com.palworldadmin.app.service.worldstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuildPlayerRef(
        String uid,
        String name
) {
}
