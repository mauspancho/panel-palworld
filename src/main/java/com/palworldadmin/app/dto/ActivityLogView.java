package com.palworldadmin.app.dto;

import com.palworldadmin.app.entity.ActionStatus;

import java.time.LocalDateTime;

public record ActivityLogView(
        LocalDateTime startedAt,
        String serverName,
        String action,
        ActionStatus status,
        String username
) {
}
