package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.PlayerOnlineSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlayerOnlineSnapshotRepository extends JpaRepository<PlayerOnlineSnapshot, Long> {
    @EntityGraph(attributePaths = {"server", "players"})
    List<PlayerOnlineSnapshot> findByCapturedAtBetweenOrderByCapturedAtAsc(LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"server", "players"})
    List<PlayerOnlineSnapshot> findByServerAndCapturedAtBetweenOrderByCapturedAtAsc(PalworldServer server, LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"players"})
    Optional<PlayerOnlineSnapshot> findByServerAndCapturedAt(PalworldServer server, LocalDateTime capturedAt);

    void deleteByServer(PalworldServer server);
}
