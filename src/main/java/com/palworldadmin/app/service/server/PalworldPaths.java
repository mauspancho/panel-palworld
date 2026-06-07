package com.palworldadmin.app.service.server;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.util.PathSecurityUtil;

import java.nio.file.Path;

public record PalworldPaths(
        Path root,
        Path settingsFile,
        Path defaultSettingsFile,
        Path saveGamesRoot
) {
    public static PalworldPaths from(PalworldServer server) {
        Path root = PathSecurityUtil.normalizeRoot(server);
        return new PalworldPaths(
                root,
                root.resolve("Pal/Saved/Config/LinuxServer/PalWorldSettings.ini").normalize(),
                root.resolve("DefaultPalWorldSettings.ini").normalize(),
                root.resolve("Pal/Saved/SaveGames/0").normalize()
        );
    }
}
