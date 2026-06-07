package com.palworldadmin.app.dto;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerStatus;

public record ServerDashboardRow(PalworldServer server, ServerStatus status) {
}
