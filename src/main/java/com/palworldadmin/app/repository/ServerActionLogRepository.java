package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerActionLogRepository extends JpaRepository<ServerActionLog, Long> {
    List<ServerActionLog> findTop100ByOrderByStartedAtDesc();
    List<ServerActionLog> findTop100ByServerOrderByStartedAtDesc(PalworldServer server);
    void deleteByServer(PalworldServer server);
}
