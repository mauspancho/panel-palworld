package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.RegisteredPlayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisteredPlayerRepository extends JpaRepository<RegisteredPlayer, Long> {
    Optional<RegisteredPlayer> findByServerAndPlayerKey(PalworldServer server, String playerKey);

    List<RegisteredPlayer> findByServerOrderByActiveDescLastSeenAtDescNameAsc(PalworldServer server);

    List<RegisteredPlayer> findByServerAndActiveTrue(PalworldServer server);

    @EntityGraph(attributePaths = {"server"})
    List<RegisteredPlayer> findByActiveTrueOrderByNameAsc();

    long countByServer(PalworldServer server);

    long countByServerAndActive(PalworldServer server, boolean active);

    void deleteByServer(PalworldServer server);
}
