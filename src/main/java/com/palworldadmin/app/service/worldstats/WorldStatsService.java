package com.palworldadmin.app.service.worldstats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palworldadmin.app.entity.PalworldServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorldStatsService {
    private static final Logger log = LoggerFactory.getLogger(WorldStatsService.class);

    private final ObjectMapper objectMapper;

    public WorldStatsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorldStatsResult aggregate(List<PalworldServer> servers) {
        int totalPlayers = 0;
        int playersWithBase = 0;
        int totalGuilds = 0;
        int availableServers = 0;
        String lastError = null;
        List<GuildStats> guilds = new ArrayList<>();

        for (PalworldServer server : servers) {
            WorldStatsResult stats = read(server);
            if (stats.available()) {
                availableServers++;
                totalPlayers += stats.totalPlayers();
                playersWithBase += stats.playersWithBase();
                totalGuilds += stats.totalGuilds();
                guilds.addAll(stats.guilds());
            } else {
                lastError = stats.message();
            }
        }

        if (availableServers == 0) {
            return WorldStatsResult.unavailable(lastError == null ? "No configurado" : lastError);
        }
        return WorldStatsResult.available(totalPlayers, playersWithBase, totalGuilds, guilds);
    }

    public WorldStatsResult read(PalworldServer server) {
        String configuredPath = server.getWorldStatsPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return WorldStatsResult.unavailable("No configurado");
        }
        try {
            Path path = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
            validateReadableFile(path);
            WorldStatsDocument document = objectMapper.readValue(path.toFile(), WorldStatsDocument.class);
            validateDocument(document);
            log.info("world-stats.json leido para servidor '{}': {} jugadores, {} con base, {} gremios con base",
                    safeServerName(server), document.totalPlayers(), document.playersWithBase(), document.totalGuilds());
            return WorldStatsResult.available(document);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "No se pudo leer world-stats.json." : e.getMessage();
            log.warn("No se pudo leer world-stats.json para servidor '{}': {}", safeServerName(server), message);
            return WorldStatsResult.unavailable(message);
        }
    }

    private void validateReadableFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Archivo de estadisticas no existe: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("La ruta de estadisticas no es un archivo regular: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IOException("Sin permisos de lectura sobre estadisticas: " + path);
        }
        if (Files.size(path) == 0) {
            throw new IOException("El archivo de estadisticas esta vacio.");
        }
    }

    private void validateDocument(WorldStatsDocument document) {
        if (document.schemaVersion() != 1) {
            throw new IllegalArgumentException("Schema de world-stats.json no soportado: " + document.schemaVersion());
        }
        if (document.totalPlayers() < 0 || document.playersWithBase() < 0 || document.totalGuilds() < 0) {
            throw new IllegalArgumentException("world-stats.json contiene totales negativos.");
        }
    }

    private String safeServerName(PalworldServer server) {
        return server == null || server.getName() == null ? "-" : server.getName();
    }
}
