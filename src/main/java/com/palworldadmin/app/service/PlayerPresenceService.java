package com.palworldadmin.app.service;

import com.palworldadmin.app.dto.RconPlayerView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.PlayerConnectionSession;
import com.palworldadmin.app.entity.RegisteredPlayer;
import com.palworldadmin.app.repository.PlayerConnectionSessionRepository;
import com.palworldadmin.app.repository.RegisteredPlayerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlayerPresenceService {
    private final RegisteredPlayerRepository players;
    private final PlayerConnectionSessionRepository sessions;
    private final Duration presenceTimeout;

    public PlayerPresenceService(
            RegisteredPlayerRepository players,
            PlayerConnectionSessionRepository sessions,
            @Value("${palworld-admin.player-presence-timeout-seconds:90}") long presenceTimeoutSeconds
    ) {
        this.players = players;
        this.sessions = sessions;
        this.presenceTimeout = Duration.ofSeconds(Math.max(30, presenceTimeoutSeconds));
    }

    @Transactional
    public void recordPresence(PalworldServer server, List<RconPlayerView> currentPlayers, LocalDateTime observedAt) {
        LocalDateTime capturedAt = observedAt.withNano(0);
        Map<String, RconPlayerView> current = currentPlayers.stream()
                .filter(player -> !playerKey(player).isBlank())
                .collect(Collectors.toMap(
                        this::playerKey,
                        player -> player,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        current.forEach((key, playerView) -> markOnline(server, key, playerView, capturedAt));

        Set<String> currentKeys = current.keySet();
        players.findByServerAndActiveTrue(server).stream()
                .filter(player -> !currentKeys.contains(player.getPlayerKey()))
                .forEach(player -> markOffline(player, capturedAt));
    }

    @Transactional
    public PlayerRegistryView registry(PalworldServer server, String range) {
        AnalyticsWindow window = AnalyticsWindow.from(range);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        expireStalePlayers(presenceTimeout, now);
        LocalDateTime start = window.start(now);
        List<PlayerSessionView> sessionViews = sessions.findOverlappingServerSessions(server, start, now).stream()
                .map(session -> sessionView(session, now))
                .toList();
        Map<String, List<PlayerSessionView>> sessionsByPlayer = sessionViews.stream()
                .collect(Collectors.groupingBy(PlayerSessionView::playerKey, LinkedHashMap::new, Collectors.toList()));

        List<RegisteredPlayerView> playerViews = players.findByServerOrderByActiveDescLastSeenAtDescNameAsc(server).stream()
                .map(player -> registeredPlayerView(player, sessionsByPlayer.getOrDefault(player.getPlayerKey(), List.of()), now))
                .sorted(Comparator.comparing(RegisteredPlayerView::active).reversed()
                        .thenComparing(RegisteredPlayerView::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RegisteredPlayerView::name))
                .toList();
        long total = players.countByServer(server);
        long active = players.countByServerAndActive(server, true);
        return new PlayerRegistryView(
                server.getId(),
                server.getName(),
                window.name,
                window.label,
                total,
                active,
                Math.max(0, total - active),
                playerViews,
                sessionViews
        );
    }

    @Transactional
    public CurrentPlayersView currentPlayers() {
        expireStalePlayers(presenceTimeout, LocalDateTime.now());
        List<String> activePlayers = players.findByActiveTrueOrderByNameAsc().stream()
                .map(player -> player.getServer().getName() + ": " + displayName(player))
                .toList();
        return new CurrentPlayersView(activePlayers.size(), activePlayers);
    }

    @Transactional
    public List<PlayerActivityPoint> activitySeries(Duration window, int bucketMinutes) {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        expireStalePlayers(presenceTimeout, now);
        LocalDateTime start = bucket(now.minus(window), bucketMinutes);
        LocalDateTime end = bucket(now, bucketMinutes);
        List<PlayerConnectionSession> sessionsInRange = sessions.findOverlappingSessions(start, now);
        List<PlayerActivityPoint> points = new ArrayList<>();
        for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(bucketMinutes)) {
            LocalDateTime bucketEnd = cursor.plusMinutes(bucketMinutes);
            Set<String> activeNames = new LinkedHashSet<>();
            for (PlayerConnectionSession session : sessionsInRange) {
                if (overlaps(session, cursor, bucketEnd, now)) {
                    activeNames.add(session.getServer().getName() + ": " + displayName(session.getPlayer()));
                }
            }
            points.add(new PlayerActivityPoint(cursor.toString(), activeNames.size(), new ArrayList<>(activeNames)));
        }
        return points;
    }

    @Transactional
    public PlayerAverageView playerAverage(String range) {
        AnalyticsWindow window = AnalyticsWindow.from(range);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        expireStalePlayers(presenceTimeout, now);
        LocalDateTime start = window.start(now);
        List<PlayerConnectionSession> sessionsInRange = sessions.findOverlappingSessions(start, now);
        List<LocalDateTime> buckets = averageBuckets(window, start, now);
        List<Long> counts = buckets.stream()
                .map(bucketStart -> {
                    LocalDateTime bucketEnd = averageBucketEnd(window, bucketStart);
                    return sessionsInRange.stream()
                            .filter(session -> overlaps(session, bucketStart, bucketEnd, now))
                            .map(session -> session.getPlayer().getPlayerKey())
                            .distinct()
                            .count();
                })
                .toList();
        long peak = counts.stream().mapToLong(Long::longValue).max().orElse(0L);
        double average = counts.isEmpty() ? 0.0 : counts.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return new PlayerAverageView(window.name, window.label, roundOneDecimal(average), peak, counts.size(), start.toString(), now.toString());
    }

    @Transactional
    public void expireStalePlayers() {
        expireStalePlayers(presenceTimeout, LocalDateTime.now());
    }

    @Transactional
    public void expireStalePlayers(Duration maxAge, LocalDateTime observedAt) {
        LocalDateTime now = observedAt.withNano(0);
        LocalDateTime cutoff = now.minus(maxAge);
        players.findByActiveTrueOrderByNameAsc().stream()
                .filter(player -> player.getLastSeenAt() == null || player.getLastSeenAt().isBefore(cutoff))
                .forEach(player -> markOffline(player, staleDisconnectedAt(player, maxAge, now)));
    }

    @Transactional
    public void deleteForServer(PalworldServer server) {
        sessions.deleteByServer(server);
        players.deleteByServer(server);
    }

    private void markOnline(PalworldServer server, String playerKey, RconPlayerView playerView, LocalDateTime capturedAt) {
        RegisteredPlayer player = players.findByServerAndPlayerKey(server, playerKey).orElseGet(() -> newPlayer(server, playerKey, capturedAt));
        player.setName(displayName(playerView));
        player.setPlayerId(blankToNull(playerView.playerId()));
        player.setPlatformId(blankToNull(playerView.platformId()));
        player.setLastSeenAt(capturedAt);
        if (!player.isActive()) {
            player.setActive(true);
            player.setLastConnectedAt(capturedAt);
            player.setLastDisconnectedAt(null);
            players.save(player);
            startSession(server, player, capturedAt);
            return;
        }
        touchActiveSession(player, capturedAt);
        players.save(player);
    }

    private RegisteredPlayer newPlayer(PalworldServer server, String playerKey, LocalDateTime capturedAt) {
        RegisteredPlayer player = new RegisteredPlayer();
        player.setServer(server);
        player.setPlayerKey(playerKey);
        player.setFirstSeenAt(capturedAt);
        player.setLastSeenAt(capturedAt);
        return player;
    }

    private void startSession(PalworldServer server, RegisteredPlayer player, LocalDateTime startedAt) {
        if (sessions.findByPlayerAndActiveTrue(player).isPresent()) {
            return;
        }
        PlayerConnectionSession session = new PlayerConnectionSession();
        session.setServer(server);
        session.setPlayer(player);
        session.setStartedAt(startedAt);
        session.setActive(true);
        sessions.save(session);
    }

    private void touchActiveSession(RegisteredPlayer player, LocalDateTime observedAt) {
        sessions.findByPlayerAndActiveTrue(player).ifPresent(session -> {
            session.setDurationSeconds(durationSeconds(session.getStartedAt(), observedAt));
            sessions.save(session);
        });
    }

    private void markOffline(RegisteredPlayer player, LocalDateTime disconnectedAt) {
        player.setActive(false);
        player.setLastDisconnectedAt(disconnectedAt);
        sessions.findByPlayerAndActiveTrue(player).ifPresent(session -> {
            session.setActive(false);
            session.setEndedAt(disconnectedAt);
            long duration = durationSeconds(session.getStartedAt(), disconnectedAt);
            session.setDurationSeconds(duration);
            player.setTotalSeconds(player.getTotalSeconds() + duration);
            sessions.save(session);
        });
        players.save(player);
    }

    private LocalDateTime staleDisconnectedAt(RegisteredPlayer player, Duration maxAge, LocalDateTime now) {
        if (player.getLastSeenAt() == null) {
            return now;
        }
        LocalDateTime disconnectedAt = player.getLastSeenAt().plus(maxAge);
        return disconnectedAt.isAfter(now) ? now : disconnectedAt;
    }

    private boolean overlaps(PlayerConnectionSession session, LocalDateTime bucketStart, LocalDateTime bucketEnd, LocalDateTime now) {
        LocalDateTime sessionStart = session.getStartedAt();
        LocalDateTime sessionEnd = session.getEndedAt() == null ? now : session.getEndedAt();
        return sessionStart != null
                && sessionStart.isBefore(bucketEnd)
                && sessionEnd.isAfter(bucketStart);
    }

    private LocalDateTime bucket(LocalDateTime value, int bucketMinutes) {
        int safeBucket = Math.max(1, bucketMinutes);
        int minute = (value.getMinute() / safeBucket) * safeBucket;
        return value.withMinute(minute).withSecond(0).withNano(0);
    }

    private List<LocalDateTime> averageBuckets(AnalyticsWindow window, LocalDateTime start, LocalDateTime now) {
        List<LocalDateTime> items = new ArrayList<>();
        if (window == AnalyticsWindow.DAY) {
            LocalDateTime first = start.withMinute(0).withSecond(0).withNano(0);
            for (LocalDateTime cursor = first; !cursor.isAfter(now); cursor = cursor.plusHours(1)) {
                items.add(cursor);
            }
            return items;
        }
        LocalDateTime first = start.toLocalDate().atStartOfDay();
        LocalDateTime last = now.toLocalDate().atStartOfDay();
        for (LocalDateTime cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
            items.add(cursor);
        }
        return items;
    }

    private LocalDateTime averageBucketEnd(AnalyticsWindow window, LocalDateTime bucketStart) {
        return window == AnalyticsWindow.DAY ? bucketStart.plusHours(1) : bucketStart.plusDays(1);
    }

    private RegisteredPlayerView registeredPlayerView(RegisteredPlayer player, List<PlayerSessionView> rangeSessions, LocalDateTime now) {
        long activeSeconds = player.isActive() && player.getLastConnectedAt() != null
                ? durationSeconds(player.getLastConnectedAt(), now)
                : 0;
        long totalSeconds = player.getTotalSeconds() + activeSeconds;
        return new RegisteredPlayerView(
                player.getPlayerKey(),
                displayName(player),
                player.getPlayerId(),
                player.getPlatformId(),
                player.isActive(),
                player.getFirstSeenAt() == null ? null : player.getFirstSeenAt().toString(),
                player.getLastSeenAt() == null ? null : player.getLastSeenAt().toString(),
                player.getLastConnectedAt() == null ? null : player.getLastConnectedAt().toString(),
                player.getLastDisconnectedAt() == null ? null : player.getLastDisconnectedAt().toString(),
                totalSeconds,
                roundHours(totalSeconds),
                rangeSessions
        );
    }

    private PlayerSessionView sessionView(PlayerConnectionSession session, LocalDateTime now) {
        LocalDateTime endedAt = session.getEndedAt() == null ? now : session.getEndedAt();
        long seconds = session.isActive() ? durationSeconds(session.getStartedAt(), now) : session.getDurationSeconds();
        return new PlayerSessionView(
                session.getPlayer().getPlayerKey(),
                displayName(session.getPlayer()),
                session.getStartedAt().toString(),
                session.getEndedAt() == null ? null : session.getEndedAt().toString(),
                session.isActive(),
                seconds,
                roundHours(seconds)
        );
    }

    private String playerKey(RconPlayerView player) {
        if (player.playerId() != null && !player.playerId().isBlank()) {
            return "player:" + player.playerId().trim();
        }
        if (player.platformId() != null && !player.platformId().isBlank()) {
            return "platform:" + player.platformId().trim();
        }
        return player.name() == null ? "" : "name:" + player.name().trim().toLowerCase();
    }

    private String displayName(RconPlayerView player) {
        if (player.name() != null && !player.name().isBlank()) {
            return player.name().trim();
        }
        if (player.playerId() != null && !player.playerId().isBlank()) {
            return player.playerId().trim();
        }
        if (player.platformId() != null && !player.platformId().isBlank()) {
            return player.platformId().trim();
        }
        return "Jugador";
    }

    private String displayName(RegisteredPlayer player) {
        if (player.getName() != null && !player.getName().isBlank()) {
            return player.getName();
        }
        if (player.getPlayerId() != null && !player.getPlayerId().isBlank()) {
            return player.getPlayerId();
        }
        if (player.getPlatformId() != null && !player.getPlatformId().isBlank()) {
            return player.getPlatformId();
        }
        return "Jugador";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long durationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return Duration.between(start, end).toSeconds();
    }

    private double roundHours(long seconds) {
        return Math.round((seconds / 3600.0) * 100.0) / 100.0;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record PlayerRegistryView(
            Long serverId,
            String serverName,
            String range,
            String label,
            long totalPlayers,
            long activePlayers,
            long inactivePlayers,
            List<RegisteredPlayerView> players,
            List<PlayerSessionView> sessions
    ) {
    }

    public record RegisteredPlayerView(
            String key,
            String name,
            String playerId,
            String platformId,
            boolean active,
            String firstSeenAt,
            String lastSeenAt,
            String lastConnectedAt,
            String lastDisconnectedAt,
            long totalSeconds,
            double totalHours,
            List<PlayerSessionView> sessions
    ) {
    }

    public record PlayerSessionView(
            String playerKey,
            String playerName,
            String startedAt,
            String endedAt,
            boolean active,
            long durationSeconds,
            double hours
    ) {
    }

    public record CurrentPlayersView(long count, List<String> players) {
    }

    public record PlayerActivityPoint(String capturedAt, long playerCount, List<String> players) {
    }

    public record PlayerAverageView(String range, String label, double averagePlayers, long peakPlayers, long sampleCount, String startedAt, String endedAt) {
    }

    private enum AnalyticsWindow {
        DAY("day", "Dia", Duration.ofDays(1)),
        WEEK("week", "Semana", Duration.ofDays(7)),
        MONTH("month", "Mes", Duration.ofDays(30));

        private final String name;
        private final String label;
        private final Duration duration;

        AnalyticsWindow(String name, String label, Duration duration) {
            this.name = name;
            this.label = label;
            this.duration = duration;
        }

        static AnalyticsWindow from(String range) {
            if (range == null) {
                return WEEK;
            }
            return switch (range.toLowerCase()) {
                case "day" -> DAY;
                case "month" -> MONTH;
                default -> WEEK;
            };
        }

        LocalDateTime start(LocalDateTime now) {
            if (this == DAY) {
                return now.minus(duration).withMinute(0);
            }
            return now.minus(duration).toLocalDate().atStartOfDay();
        }
    }
}
