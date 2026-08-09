package com.palworldadmin.app.service.worldstats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorldStatsServiceTest {
    @TempDir
    Path tempDir;

    private final WorldStatsService service = new WorldStatsService(new ObjectMapper());

    @Test
    void readsValidWorldStatsJson() throws Exception {
        Path stats = tempDir.resolve("world-stats.json");
        Files.writeString(stats, """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-08-08T22:35:00-06:00",
                  "saveLastModified": "2026-08-08T22:34:30-06:00",
                  "totalPlayers": 100,
                  "playersWithBase": 20,
                  "totalGuilds": 5,
                  "guilds": []
                }
                """);

        WorldStatsResult result = service.read(server(stats));

        assertThat(result.available()).isTrue();
        assertThat(result.totalPlayers()).isEqualTo(100);
        assertThat(result.playersWithBase()).isEqualTo(20);
        assertThat(result.totalGuilds()).isEqualTo(5);
        assertThat(result.generatedAt()).isEqualTo("2026-08-08T22:35:00-06:00");
        assertThat(result.saveLastModified()).isEqualTo("2026-08-08T22:34:30-06:00");
    }

    @Test
    void missingPathReturnsControlledFallback() {
        WorldStatsResult result = service.read(server(null));

        assertThat(result.available()).isFalse();
        assertThat(result.message()).isEqualTo("No configurado");
    }

    @Test
    void missingFileReturnsControlledFallback() {
        WorldStatsResult result = service.read(server(tempDir.resolve("missing-world-stats.json")));

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("no existe");
    }

    @Test
    void invalidJsonReturnsControlledFallback() throws Exception {
        Path stats = tempDir.resolve("broken.json");
        Files.writeString(stats, "{not-json");

        WorldStatsResult result = service.read(server(stats));

        assertThat(result.available()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    private PalworldServer server(Path statsPath) {
        PalworldServer server = new PalworldServer();
        server.setId(1L);
        server.setName("World Stats Test");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-world-stats-test.service");
        server.setRootPath("/home/usuario/palworld");
        server.setWorldStatsPath(statsPath == null ? null : statsPath.toString());
        return server;
    }
}
