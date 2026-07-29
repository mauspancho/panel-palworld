package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.RconWelcomeMessageConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RconWelcomeMessageConfigRepository extends JpaRepository<RconWelcomeMessageConfig, Long> {
    Optional<RconWelcomeMessageConfig> findByServer(PalworldServer server);

    void deleteByServer(PalworldServer server);
}
