package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.PlayerConnectionSession;
import com.palworldadmin.app.entity.RegisteredPlayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlayerConnectionSessionRepository extends JpaRepository<PlayerConnectionSession, Long> {
    Optional<PlayerConnectionSession> findByPlayerAndActiveTrue(RegisteredPlayer player);

    @EntityGraph(attributePaths = {"player"})
    @Query("""
            select session from PlayerConnectionSession session
            where session.server = :server
              and session.startedAt <= :end
              and (session.endedAt is null or session.endedAt >= :start)
            order by session.startedAt desc
            """)
    List<PlayerConnectionSession> findOverlappingServerSessions(
            @Param("server") PalworldServer server,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @EntityGraph(attributePaths = {"server", "player"})
    @Query("""
            select session from PlayerConnectionSession session
            where session.startedAt <= :end
              and (session.endedAt is null or session.endedAt >= :start)
            order by session.startedAt asc
            """)
    List<PlayerConnectionSession> findOverlappingSessions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    void deleteByServer(PalworldServer server);
}
