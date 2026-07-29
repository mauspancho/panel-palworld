package com.palworldadmin.app.service;

import com.palworldadmin.app.dto.RconPlayerView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.PlayerOnlineSnapshot;
import com.palworldadmin.app.entity.PlayerOnlineSnapshotPlayer;
import com.palworldadmin.app.repository.PlayerOnlineSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlayerOnlineSnapshotService {
    private final PlayerOnlineSnapshotRepository snapshots;

    public PlayerOnlineSnapshotService(PlayerOnlineSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public void recordSnapshot(PalworldServer server, List<RconPlayerView> players, LocalDateTime capturedAt) {
        PlayerOnlineSnapshot snapshot = snapshots.findByServerAndCapturedAt(server, capturedAt)
                .orElseGet(() -> newSnapshot(server, capturedAt));
        snapshot.setPlayerCount(players.size());
        snapshot.getPlayers().clear();
        for (RconPlayerView player : players) {
            PlayerOnlineSnapshotPlayer item = new PlayerOnlineSnapshotPlayer();
            item.setPlayerName(player.name());
            item.setPlayerId(player.playerId());
            item.setPlatformId(player.platformId());
            item.setRaw(player.raw());
            snapshot.addPlayer(item);
        }
        snapshots.save(snapshot);
    }

    private PlayerOnlineSnapshot newSnapshot(PalworldServer server, LocalDateTime capturedAt) {
        PlayerOnlineSnapshot snapshot = new PlayerOnlineSnapshot();
        snapshot.setServer(server);
        snapshot.setCapturedAt(capturedAt);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<PlayerSnapshotPoint> recentSeries(Duration window, int bucketMinutes) {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime start = bucket(now.minus(window), bucketMinutes);
        LocalDateTime end = bucket(now, bucketMinutes);
        Map<LocalDateTime, Bucket> buckets = new LinkedHashMap<>();
        for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(bucketMinutes)) {
            buckets.put(cursor, new Bucket());
        }

        snapshots.findByCapturedAtBetweenOrderByCapturedAtAsc(start, now).forEach(snapshot -> {
            LocalDateTime bucket = bucket(snapshot.getCapturedAt(), bucketMinutes);
            Bucket item = buckets.get(bucket);
            if (item == null) {
                return;
            }
            item.count += snapshot.getPlayerCount();
            snapshot.getPlayers().forEach(player -> {
                String name = player.getPlayerName();
                if (name == null || name.isBlank()) {
                    name = player.getPlayerId();
                }
                if (name == null || name.isBlank()) {
                    name = player.getPlatformId();
                }
                if (name != null && !name.isBlank()) {
                    item.players.add(snapshot.getServer().getName() + ": " + name);
                }
            });
        });

        return buckets.entrySet().stream()
                .map(entry -> new PlayerSnapshotPoint(entry.getKey().toString(), entry.getValue().count, new ArrayList<>(entry.getValue().players)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerDurationAnalytics playerDurations(PalworldServer server, String range) {
        AnalyticsWindow window = AnalyticsWindow.from(range);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime start = window.start(now);
        Map<String, PlayerAccumulator> players = new LinkedHashMap<>();
        List<String> buckets = window.buckets(start, now);

        snapshots.findByServerAndCapturedAtBetweenOrderByCapturedAtAsc(server, start, now).forEach(snapshot -> {
            String bucket = window.bucket(snapshot.getCapturedAt());
            snapshot.getPlayers().forEach(player -> {
                String key = playerKey(player);
                if (key.isBlank()) {
                    return;
                }
                PlayerAccumulator accumulator = players.computeIfAbsent(key, ignored -> new PlayerAccumulator(player, buckets));
                accumulator.add(bucket, snapshot.getCapturedAt(), 15);
            });
        });

        List<PlayerDurationView> views = players.entrySet().stream()
                .map(entry -> entry.getValue().toView(entry.getKey()))
                .sorted(Comparator.comparingLong(PlayerDurationView::totalMinutes).reversed().thenComparing(PlayerDurationView::name))
                .toList();
        return new PlayerDurationAnalytics(window.name, window.label, 15, views);
    }

    @Transactional(readOnly = true)
    public PlayerAverageView playerAverage(String range) {
        AnalyticsWindow window = AnalyticsWindow.from(range);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime start = window.start(now);
        Map<LocalDateTime, Long> totalsByCapture = new LinkedHashMap<>();
        snapshots.findByCapturedAtBetweenOrderByCapturedAtAsc(start, now).forEach(snapshot ->
                totalsByCapture.merge(snapshot.getCapturedAt().withSecond(0).withNano(0), (long) snapshot.getPlayerCount(), Long::sum)
        );

        long bucketCount = totalsByCapture.size();
        long peak = totalsByCapture.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        double average = bucketCount == 0
                ? 0.0
                : totalsByCapture.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
        return new PlayerAverageView(window.name, window.label, roundOneDecimal(average), peak, bucketCount, start.toString(), now.toString());
    }

    @Transactional
    public void deleteForServer(PalworldServer server) {
        snapshots.deleteByServer(server);
    }

    private LocalDateTime bucket(LocalDateTime value, int bucketMinutes) {
        int safeBucket = Math.max(1, bucketMinutes);
        int minute = (value.getMinute() / safeBucket) * safeBucket;
        return value.withMinute(minute).withSecond(0).withNano(0);
    }

    private String playerKey(PlayerOnlineSnapshotPlayer player) {
        if (player.getPlayerId() != null && !player.getPlayerId().isBlank()) {
            return "player:" + player.getPlayerId().trim();
        }
        if (player.getPlatformId() != null && !player.getPlatformId().isBlank()) {
            return "platform:" + player.getPlatformId().trim();
        }
        return player.getPlayerName() == null ? "" : "name:" + player.getPlayerName().trim().toLowerCase();
    }

    private static String displayName(PlayerOnlineSnapshotPlayer player) {
        if (player.getPlayerName() != null && !player.getPlayerName().isBlank()) {
            return player.getPlayerName();
        }
        if (player.getPlayerId() != null && !player.getPlayerId().isBlank()) {
            return player.getPlayerId();
        }
        if (player.getPlatformId() != null && !player.getPlatformId().isBlank()) {
            return player.getPlatformId();
        }
        return "Jugador";
    }

    private static class Bucket {
        private long count;
        private final Set<String> players = new LinkedHashSet<>();
    }

    public record PlayerSnapshotPoint(String capturedAt, long playerCount, List<String> players) {
    }

    public record PlayerDurationAnalytics(String range, String label, int snapshotMinutes, List<PlayerDurationView> players) {
    }

    public record PlayerAverageView(String range, String label, double averagePlayers, long peakPlayers, long sampleCount, String startedAt, String endedAt) {
    }

    public record PlayerDurationView(
            String key,
            String name,
            String playerId,
            String platformId,
            long totalMinutes,
            double totalHours,
            List<PlayerDurationPoint> series,
            List<PlayerSessionView> sessions
    ) {
    }

    public record PlayerDurationPoint(String bucket, long minutes, double hours) {
    }

    public record PlayerSessionView(String startedAt, String endedAt, long minutes, double hours) {
    }

    private static class PlayerAccumulator {
        private final String name;
        private final String playerId;
        private final String platformId;
        private final Map<String, Long> minutesByBucket = new LinkedHashMap<>();
        private final List<LocalDateTime> capturedTimes = new ArrayList<>();

        private PlayerAccumulator(PlayerOnlineSnapshotPlayer player, List<String> buckets) {
            this.name = displayName(player);
            this.playerId = player.getPlayerId();
            this.platformId = player.getPlatformId();
            buckets.forEach(bucket -> minutesByBucket.put(bucket, 0L));
        }

        private void add(String bucket, LocalDateTime capturedAt, long minutes) {
            minutesByBucket.put(bucket, minutesByBucket.getOrDefault(bucket, 0L) + minutes);
            capturedTimes.add(capturedAt.withSecond(0).withNano(0));
        }

        private PlayerDurationView toView(String key) {
            long total = minutesByBucket.values().stream().mapToLong(Long::longValue).sum();
            List<PlayerDurationPoint> series = minutesByBucket.entrySet().stream()
                    .map(entry -> new PlayerDurationPoint(entry.getKey(), entry.getValue(), roundHours(entry.getValue())))
                    .toList();
            return new PlayerDurationView(key, name, playerId, platformId, total, roundHours(total), series, sessions());
        }

        private List<PlayerSessionView> sessions() {
            if (capturedTimes.isEmpty()) {
                return List.of();
            }
            List<LocalDateTime> times = capturedTimes.stream()
                    .distinct()
                    .sorted()
                    .toList();
            List<PlayerSessionView> sessions = new ArrayList<>();
            LocalDateTime start = times.get(0);
            LocalDateTime previous = start;
            for (int index = 1; index < times.size(); index++) {
                LocalDateTime current = times.get(index);
                if (Duration.between(previous, current).toMinutes() > 20) {
                    sessions.add(session(start, previous));
                    start = current;
                }
                previous = current;
            }
            sessions.add(session(start, previous));
            return sessions.stream()
                    .sorted(Comparator.comparing(PlayerSessionView::startedAt).reversed())
                    .toList();
        }

        private PlayerSessionView session(LocalDateTime start, LocalDateTime lastSnapshot) {
            LocalDateTime end = lastSnapshot.plusMinutes(15);
            long minutes = Math.max(15, Duration.between(start, end).toMinutes());
            return new PlayerSessionView(start.toString(), end.toString(), minutes, roundHours(minutes));
        }
    }

    private static double roundHours(long minutes) {
        return Math.round((minutes / 60.0) * 100.0) / 100.0;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
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

        List<String> buckets(LocalDateTime start, LocalDateTime now) {
            List<String> items = new ArrayList<>();
            if (this == DAY) {
                for (LocalDateTime cursor = start; !cursor.isAfter(now); cursor = cursor.plusHours(1)) {
                    items.add(cursor.withMinute(0).withSecond(0).withNano(0).toString());
                }
                return items;
            }
            LocalDate end = now.toLocalDate();
            for (LocalDate cursor = start.toLocalDate(); !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                items.add(cursor.toString());
            }
            return items;
        }

        String bucket(LocalDateTime capturedAt) {
            if (this == DAY) {
                return capturedAt.withMinute(0).withSecond(0).withNano(0).toString();
            }
            return capturedAt.toLocalDate().toString();
        }
    }
}
