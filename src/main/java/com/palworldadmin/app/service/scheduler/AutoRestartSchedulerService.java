package com.palworldadmin.app.service.scheduler;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.rcon.RconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AutoRestartSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(AutoRestartSchedulerService.class);
    private static final int WARNING_MINUTES = 15;
    private static final String WARNING_MESSAGE = "En 15 min se reiniciara el servidor de forma automatica. Toma precauciones.";

    private final PalworldServerRepository servers;
    private final PalworldServerService serverService;
    private final RconService rcon;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AutoRestartSchedulerService(PalworldServerRepository servers, PalworldServerService serverService, RconService rcon) {
        this.servers = servers;
        this.serverService = serverService;
        this.rcon = rcon;
    }

    @Scheduled(
            fixedDelayString = "${palworld-admin.auto-restart-scheduler-interval-ms:60000}",
            initialDelayString = "${palworld-admin.auto-restart-scheduler-initial-delay-ms:30000}"
    )
    public void runSchedule() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            runScheduleAt(LocalDateTime.now());
        } finally {
            running.set(false);
        }
    }

    void runScheduleAt(LocalDateTime now) {
        LocalDateTime currentMinute = now.withSecond(0).withNano(0);
        servers.findAllByOrderByNameAsc().stream()
                .filter(PalworldServer::isEnabled)
                .filter(PalworldServer::isAutoRestartEnabled)
                .filter(server -> server.getAutoRestartTime() != null && !server.getAutoRestartTime().isBlank())
                .forEach(server -> processServer(server, currentMinute));
    }

    private void processServer(PalworldServer server, LocalDateTime now) {
        LocalTime restartTime = parseTime(server);
        if (restartTime == null) {
            return;
        }
        List<LocalDate> targetDates = List.of(now.toLocalDate(), now.toLocalDate().plusDays(1));
        targetDates.forEach(targetDate -> processTarget(server, targetDate, restartTime, now));
    }

    private void processTarget(PalworldServer server, LocalDate targetDate, LocalTime restartTime, LocalDateTime now) {
        LocalDateTime restartAt = LocalDateTime.of(targetDate, restartTime);
        LocalDateTime warningAt = restartAt.minusMinutes(WARNING_MINUTES);
        if (shouldWarn(server, targetDate, warningAt, restartAt, now)) {
            sendWarning(server, targetDate);
        }
        if (shouldRestart(server, targetDate, restartAt, now)) {
            restart(server, targetDate);
        }
    }

    private boolean shouldWarn(PalworldServer server, LocalDate targetDate, LocalDateTime warningAt, LocalDateTime restartAt, LocalDateTime now) {
        return now.equals(warningAt)
                && !targetDate.equals(server.getAutoRestartLastWarningDate());
    }

    private boolean shouldRestart(PalworldServer server, LocalDate targetDate, LocalDateTime restartAt, LocalDateTime now) {
        return now.equals(restartAt)
                && !targetDate.equals(server.getAutoRestartLastRunDate());
    }

    private void sendWarning(PalworldServer server, LocalDate targetDate) {
        try {
            rcon.broadcast(server.getId(), WARNING_MESSAGE);
            log.info("Aviso de reinicio automatico enviado para {}", server.getName());
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar aviso RCON de reinicio automatico para {}: {}", server.getName(), e.getMessage());
        } finally {
            serverService.markAutoRestartWarningSent(server.getId(), targetDate);
        }
    }

    private void restart(PalworldServer server, LocalDate targetDate) {
        try {
            serverService.action(server.getId(), "restart", "scheduler");
            serverService.markAutoRestartRan(server.getId(), targetDate);
            log.info("Reinicio automatico ejecutado para {}", server.getName());
        } catch (RuntimeException e) {
            log.warn("No se pudo ejecutar reinicio automatico para {}: {}", server.getName(), e.getMessage());
        }
    }

    private LocalTime parseTime(PalworldServer server) {
        try {
            return LocalTime.parse(server.getAutoRestartTime());
        } catch (DateTimeParseException e) {
            log.warn("Hora de reinicio automatico invalida para {}: {}", server.getName(), server.getAutoRestartTime());
            return null;
        }
    }
}
