package com.palworldadmin.app.repository;

import com.palworldadmin.app.dto.ActivityLogView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ServerActionLogRepository extends JpaRepository<ServerActionLog, Long> {
    List<ServerActionLog> findTop100ByOrderByStartedAtDesc();
    List<ServerActionLog> findTop100ByServerOrderByStartedAtDesc(PalworldServer server);

    @Query(
            value = "select new com.palworldadmin.app.dto.ActivityLogView(l.startedAt, s.name, l.action, l.status, l.username) " +
                    "from ServerActionLog l left join l.server s order by l.startedAt desc",
            countQuery = "select count(l) from ServerActionLog l"
    )
    Page<ActivityLogView> findRecentActivity(Pageable pageable);

    void deleteByServer(PalworldServer server);
}
