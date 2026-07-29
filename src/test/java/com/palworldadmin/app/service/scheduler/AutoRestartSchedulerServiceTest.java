package com.palworldadmin.app.service.scheduler;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.rcon.RconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoRestartSchedulerServiceTest {
    private PalworldServerRepository servers;
    private PalworldServerService serverService;
    private RconService rcon;
    private AutoRestartSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        servers = mock(PalworldServerRepository.class);
        serverService = mock(PalworldServerService.class);
        rcon = mock(RconService.class);
        scheduler = new AutoRestartSchedulerService(servers, serverService, rcon);
    }

    @Test
    void restartsOnlyAtConfiguredMinute() {
        PalworldServer server = autoRestartServer("04:00");
        when(servers.findAllByOrderByNameAsc()).thenReturn(List.of(server));

        scheduler.runScheduleAt(LocalDateTime.of(2026, 7, 27, 4, 0, 45));

        verify(serverService).action(1L, "restart", "scheduler");
        verify(serverService).markAutoRestartRan(1L, LocalDate.of(2026, 7, 27));
    }

    @Test
    void doesNotRestartAfterConfiguredMinute() {
        PalworldServer server = autoRestartServer("04:00");
        when(servers.findAllByOrderByNameAsc()).thenReturn(List.of(server));

        scheduler.runScheduleAt(LocalDateTime.of(2026, 7, 27, 4, 1, 0));

        verify(serverService, never()).action(anyLong(), eq("restart"), anyString());
        verify(serverService, never()).markAutoRestartRan(anyLong(), eq(LocalDate.of(2026, 7, 27)));
    }

    @Test
    void doesNotRestartAgainWhenTodayAlreadyRan() {
        PalworldServer server = autoRestartServer("04:00");
        server.setAutoRestartLastRunDate(LocalDate.of(2026, 7, 27));
        when(servers.findAllByOrderByNameAsc()).thenReturn(List.of(server));

        scheduler.runScheduleAt(LocalDateTime.of(2026, 7, 27, 4, 0, 0));

        verify(serverService, never()).action(anyLong(), eq("restart"), anyString());
    }

    @Test
    void sendsWarningAtExactWarningMinute() {
        PalworldServer server = autoRestartServer("04:00");
        when(servers.findAllByOrderByNameAsc()).thenReturn(List.of(server));

        scheduler.runScheduleAt(LocalDateTime.of(2026, 7, 27, 3, 45, 5));

        verify(rcon).broadcast(eq(1L), eq("En 15 min se reiniciara el servidor de forma automatica. Toma precauciones."));
        verify(serverService).markAutoRestartWarningSent(1L, LocalDate.of(2026, 7, 27));
    }

    @Test
    void doesNotSendWarningAfterWarningMinute() {
        PalworldServer server = autoRestartServer("04:00");
        when(servers.findAllByOrderByNameAsc()).thenReturn(List.of(server));

        scheduler.runScheduleAt(LocalDateTime.of(2026, 7, 27, 3, 46, 0));

        verify(rcon, never()).broadcast(anyLong(), anyString());
        verify(serverService, never()).markAutoRestartWarningSent(anyLong(), eq(LocalDate.of(2026, 7, 27)));
    }

    private PalworldServer autoRestartServer(String restartTime) {
        PalworldServer server = new PalworldServer();
        server.setId(1L);
        server.setName("palworld");
        server.setEnabled(true);
        server.setAutoRestartEnabled(true);
        server.setAutoRestartTime(restartTime);
        return server;
    }
}
