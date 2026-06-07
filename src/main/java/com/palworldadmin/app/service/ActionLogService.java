package com.palworldadmin.app.service;

import com.palworldadmin.app.entity.ActionStatus;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerActionLog;
import com.palworldadmin.app.repository.ServerActionLogRepository;
import com.palworldadmin.app.util.CommandResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActionLogService {
    private final ServerActionLogRepository logs;

    public ActionLogService(ServerActionLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public ServerActionLog started(PalworldServer server, String action, String username) {
        ServerActionLog log = new ServerActionLog();
        log.setServer(server);
        log.setAction(action);
        log.setUsername(username);
        log.setStatus(ActionStatus.RUNNING);
        log.setStartedAt(LocalDateTime.now());
        log.setMessage("Ejecutando " + action);
        return logs.save(log);
    }

    @Transactional
    public void finish(ServerActionLog log, CommandResult result) {
        log.setStatus(result.success() ? ActionStatus.SUCCESS : ActionStatus.FAILED);
        log.setFinishedAt(LocalDateTime.now());
        log.setMessage(result.success() ? "AcciÃ³n completada" : "La acciÃ³n fallÃ³");
        log.setOutput(result.stdout());
        log.setError(result.stderr());
        logs.save(log);
    }

    @Transactional
    public void fail(ServerActionLog log, Exception e) {
        log.setStatus(ActionStatus.FAILED);
        log.setFinishedAt(LocalDateTime.now());
        log.setMessage("La acciÃ³n fallÃ³");
        log.setError(e.getMessage());
        logs.save(log);
    }

    public List<ServerActionLog> recent(PalworldServer server) {
        return logs.findTop100ByServerOrderByStartedAtDesc(server);
    }

    public List<ServerActionLog> recentAll() {
        return logs.findTop100ByOrderByStartedAtDesc();
    }

    @Transactional
    public void deleteForServer(PalworldServer server) {
        logs.deleteByServer(server);
    }
}
