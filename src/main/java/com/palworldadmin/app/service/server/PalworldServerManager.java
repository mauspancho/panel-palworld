package com.palworldadmin.app.service.server;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.util.CommandResult;

public interface PalworldServerManager {
    boolean supports(PalworldServer server);
    ServerStatus status(PalworldServer server);
    CommandResult start(PalworldServer server);
    CommandResult stop(PalworldServer server);
    CommandResult restart(PalworldServer server);
    CommandResult logs(PalworldServer server, int lines);
    CommandResult update(PalworldServer server);
    CommandResult install(PalworldServer server);
    CommandResult fixPermissions(PalworldServer server);
}
