package com.palworldadmin.app.service.rcon;

import com.palworldadmin.app.dto.RconPlayerView;
import com.palworldadmin.app.dto.RconPlayersView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.service.PalworldServerService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class RconService {
    private static final Duration PLAYER_CACHE_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(2);

    private final PalworldServerService servers;
    private final ConcurrentMap<Long, ReentrantLock> serverLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CachedPlayers> playerCache = new ConcurrentHashMap<>();

    public RconService(PalworldServerService servers) {
        this.servers = servers;
    }

    public RconPlayersView players(Long serverId) {
        PalworldServer server = servers.get(serverId);
        if (!isConfigured(server)) {
            return RconPlayersView.disabled(server.getId(), server.getName());
        }
        CachedPlayers cached = playerCache.get(server.getId());
        if (cached != null && cached.isFresh()) {
            return cached.view();
        }
        return withServerLock(server.getId(), () -> {
            CachedPlayers lockedCached = playerCache.get(server.getId());
            if (lockedCached != null && lockedCached.isFresh()) {
                return lockedCached.view();
            }
            try (SourceRconClient client = client(server)) {
                String raw = client.command("ShowPlayers");
                List<RconPlayerView> players = parsePlayers(raw);
                RconPlayersView view = new RconPlayersView(server.getId(), server.getName(), true, true, "OK", players, raw, LocalDateTime.now());
                playerCache.put(server.getId(), new CachedPlayers(view, System.nanoTime()));
                return view;
            } catch (Exception e) {
                return RconPlayersView.failed(server.getId(), server.getName(), e.getMessage());
            }
        });
    }

    public String broadcast(Long serverId, String message) {
        PalworldServer server = servers.get(serverId);
        if (!isConfigured(server)) {
            throw new RconException("RCON no esta configurado para este servidor.");
        }
        String sanitized = sanitizeMessage(message);
        return withServerLock(server.getId(), () -> {
            try (SourceRconClient client = client(server)) {
                return client.command("Broadcast " + sanitized);
            } catch (Exception e) {
                throw new RconException(e.getMessage(), e);
            }
        });
    }

    private <T> T withServerLock(Long serverId, Supplier<T> action) {
        ReentrantLock lock = serverLocks.computeIfAbsent(serverId, ignored -> new ReentrantLock());
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RconException("RCON interrumpido mientras esperaba conexion disponible.", e);
        }
        if (!locked) {
            throw new RconException("RCON ocupado por otra consulta. Intenta nuevamente en unos segundos.");
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private SourceRconClient client(PalworldServer server) {
        return new SourceRconClient(server.getRconHost(), server.getRconPort(), server.getRconPassword());
    }

    private boolean isConfigured(PalworldServer server) {
        return server.isRconEnabled()
                && server.getRconHost() != null
                && server.getRconPort() != null
                && server.getRconPassword() != null
                && !server.getRconPassword().isBlank();
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new RconException("El mensaje no puede estar vacio.");
        }
        String sanitized = message.replace("\r", " ").replace("\n", " ").trim();
        if (sanitized.length() > 300) {
            throw new RconException("El mensaje RCON no puede exceder 300 caracteres.");
        }
        return sanitized;
    }

    private List<RconPlayerView> parsePlayers(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.toLowerCase().startsWith("name,"))
                .map(this::parsePlayerLine)
                .toList();
    }

    private RconPlayerView parsePlayerLine(String line) {
        String[] parts = line.split(",", -1);
        String name = parts.length > 0 ? parts[0].trim() : line;
        String playerId = parts.length > 1 ? parts[1].trim() : "";
        String platformId = parts.length > 2 ? parts[2].trim() : "";
        return new RconPlayerView(name, playerId, platformId, line);
    }

    private record CachedPlayers(RconPlayersView view, long capturedAtNanos) {
        private boolean isFresh() {
            return Duration.ofNanos(System.nanoTime() - capturedAtNanos).compareTo(PLAYER_CACHE_TTL) <= 0;
        }
    }
}
