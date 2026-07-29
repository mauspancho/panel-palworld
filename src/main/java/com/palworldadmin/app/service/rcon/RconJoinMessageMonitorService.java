package com.palworldadmin.app.service.rcon;

import com.palworldadmin.app.dto.RconPlayerView;
import com.palworldadmin.app.dto.RconPlayersView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.service.PlayerOnlineSnapshotService;
import com.palworldadmin.app.service.PlayerPresenceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class RconJoinMessageMonitorService {
    private static final Logger log = LoggerFactory.getLogger(RconJoinMessageMonitorService.class);

    private final PalworldServerRepository servers;
    private final RconService rcon;
    private final RconWelcomeMessageService welcomeMessages;
    private final PlayerPresenceService presence;
    private final PlayerOnlineSnapshotService snapshots;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, Set<String>> previousPlayers = new ConcurrentHashMap<>();
    private final Set<Long> initializedServers = ConcurrentHashMap.newKeySet();
    private final Map<String, ScheduledFuture<?>> pendingMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService delayedExecutor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "rcon-join-welcome-message");
        thread.setDaemon(true);
        return thread;
    });

    public RconJoinMessageMonitorService(
            PalworldServerRepository servers,
            RconService rcon,
            RconWelcomeMessageService welcomeMessages,
            PlayerPresenceService presence,
            PlayerOnlineSnapshotService snapshots
    ) {
        this.servers = servers;
        this.rcon = rcon;
        this.welcomeMessages = welcomeMessages;
        this.presence = presence;
        this.snapshots = snapshots;
    }

    @Scheduled(
            fixedDelayString = "${palworld-admin.rcon-join-monitor-interval-ms:10000}",
            initialDelayString = "${palworld-admin.rcon-join-monitor-initial-delay-ms:20000}"
    )
    public void pollJoins() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            servers.findAllByOrderByNameAsc().stream()
                    .filter(PalworldServer::isEnabled)
                    .filter(PalworldServer::isRconEnabled)
                    .filter(this::hasRconConnectionData)
                    .forEach(this::pollServer);
        } finally {
            presence.expireStalePlayers();
            running.set(false);
        }
    }

    @PreDestroy
    void shutdown() {
        delayedExecutor.shutdownNow();
    }

    private void pollServer(PalworldServer server) {
        RconPlayersView view = rcon.players(server.getId());
        if (!view.success()) {
            log.debug("No se pudo consultar entradas RCON para {}: {}", server.getName(), view.message());
            return;
        }

        java.time.LocalDateTime observedAt = java.time.LocalDateTime.now();
        presence.recordPresence(server, view.players(), observedAt);
        snapshots.recordSnapshot(server, view.players(), snapshotTime(observedAt));

        Optional<Integer> delay = welcomeMessages.delaySeconds(server.getId());
        if (delay.isEmpty()) {
            initializedServers.remove(server.getId());
            previousPlayers.put(server.getId(), Set.copyOf(currentPlayers(view).keySet()));
            return;
        }

        Map<String, RconPlayerView> currentPlayers = currentPlayers(view);
        Set<String> currentKeys = currentPlayers.keySet();
        if (initializedServers.add(server.getId())) {
            previousPlayers.put(server.getId(), Set.copyOf(currentKeys));
            return;
        }

        Set<String> previousKeys = previousPlayers.getOrDefault(server.getId(), Set.of());
        currentPlayers.forEach((playerKey, player) -> {
            if (!previousKeys.contains(playerKey)) {
                scheduleWelcome(server.getId(), playerKey, player, delay.get());
            }
        });
        previousPlayers.put(server.getId(), Set.copyOf(currentKeys));
    }

    private void scheduleWelcome(Long serverId, String playerKey, RconPlayerView player, int delaySeconds) {
        String pendingKey = serverId + ":" + playerKey;
        if (pendingMessages.containsKey(pendingKey)) {
            return;
        }
        ScheduledFuture<?> future = delayedExecutor.schedule(
                () -> sendIfStillOnline(serverId, playerKey, player, pendingKey),
                Math.max(0, delaySeconds),
                TimeUnit.SECONDS
        );
        pendingMessages.put(pendingKey, future);
    }

    private void sendIfStillOnline(Long serverId, String playerKey, RconPlayerView joinedPlayer, String pendingKey) {
        try {
            RconPlayersView view = rcon.players(serverId);
            if (!view.success()) {
                log.debug("No se pudo confirmar jugador conectado antes de bienvenida RCON: {}", view.message());
                return;
            }
            Map<String, RconPlayerView> currentPlayers = currentPlayers(view);
            RconPlayerView currentPlayer = currentPlayers.getOrDefault(playerKey, joinedPlayer);
            if (!currentPlayers.containsKey(playerKey)) {
                return;
            }
            welcomeMessages.nextMessage(serverId, currentPlayer)
                    .ifPresent(message -> {
                        try {
                            rcon.broadcast(serverId, message);
                        } catch (RuntimeException e) {
                            log.warn("No se pudo enviar mensaje de bienvenida RCON para servidor {}: {}", serverId, e.getMessage());
                        }
                    });
        } finally {
            pendingMessages.remove(pendingKey);
        }
    }

    private Map<String, RconPlayerView> currentPlayers(RconPlayersView view) {
        return view.players().stream()
                .collect(Collectors.toMap(
                        this::playerKey,
                        player -> player,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String playerKey(RconPlayerView player) {
        if (player.playerId() != null && !player.playerId().isBlank()) {
            return "id:" + player.playerId().trim();
        }
        if (player.platformId() != null && !player.platformId().isBlank()) {
            return "platform:" + player.platformId().trim();
        }
        return "name:" + player.name().trim().toLowerCase();
    }

    private boolean hasRconConnectionData(PalworldServer server) {
        return server.getRconHost() != null
                && !server.getRconHost().isBlank()
                && server.getRconPort() != null
                && server.getRconPassword() != null
                && !server.getRconPassword().isBlank();
    }

    private java.time.LocalDateTime snapshotTime(java.time.LocalDateTime value) {
        int minute = (value.getMinute() / 15) * 15;
        return value.withMinute(minute).withSecond(0).withNano(0);
    }
}
