package com.palworldadmin.app.controller;

import com.palworldadmin.app.dto.ActivityLogView;
import com.palworldadmin.app.dto.ServerDashboardRow;
import com.palworldadmin.app.entity.ActionStatus;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerActionLog;
import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.PlayerOnlineSnapshotService;
import com.palworldadmin.app.service.PlayerPresenceService;
import com.palworldadmin.app.service.ServerLogFilterService;
import com.palworldadmin.app.service.UserAccountService;
import com.palworldadmin.app.util.CommandResult;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final PalworldServerService servers;
    private final ActionLogService actionLogs;
    private final PlayerOnlineSnapshotService playerSnapshots;
    private final PlayerPresenceService playerPresence;
    private final ServerLogFilterService serverLogFilter;
    private final UserAccountService accounts;

    public ApiController(PalworldServerService servers, ActionLogService actionLogs, PlayerOnlineSnapshotService playerSnapshots, PlayerPresenceService playerPresence, ServerLogFilterService serverLogFilter, UserAccountService accounts) {
        this.servers = servers;
        this.actionLogs = actionLogs;
        this.playerSnapshots = playerSnapshots;
        this.playerPresence = playerPresence;
        this.serverLogFilter = serverLogFilter;
        this.accounts = accounts;
    }

    @GetMapping("/auth/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @GetMapping("/auth/me")
    public UserSessionView me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(Object::toString)
                .toList();
        try {
            UserAccountService.UserView user = accounts.profile(authentication.getName());
            return new UserSessionView(
                    user.username(),
                    user.displayName(),
                    user.email(),
                    user.role().name(),
                    roles,
                    user.enabled(),
                    user.locked(),
                    user.mustChangePassword()
            );
        } catch (IllegalArgumentException e) {
            return new UserSessionView(authentication.getName(), authentication.getName(), "", roles.contains("ROLE_ADMIN") ? "ADMIN" : "USER", roles, true, false, false);
        }
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(
            @RequestParam(defaultValue = "0") int logPage,
            @RequestParam(defaultValue = "10") int logSize
    ) {
        List<ServerView> serverViews = servers.dashboardRows().stream()
                .map(this::serverView)
                .toList();
        int safeLogSize = safeLogSize(logSize);
        Page<ActivityLogView> recent = actionLogs.recentAll(Math.max(0, logPage), safeLogSize);
        List<ActivityView> activity = recent.getContent().stream()
                .map(this::activityView)
                .toList();
        return new DashboardView(
                serverViews,
                dashboardStats(serverViews),
                activity,
                activitySeries(),
                new PageView(recent.getNumber(), recent.getSize(), recent.getTotalElements(), recent.getTotalPages())
        );
    }

    @GetMapping("/servers")
    public List<ServerView> serverList() {
        return servers.dashboardRows().stream()
                .map(this::serverView)
                .toList();
    }

    @PostMapping("/servers/{id}/actions/{action:start|stop|restart|update|install}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ActionResultView> serverAction(@PathVariable Long id, @PathVariable String action, Principal principal, Authentication authentication) {
        ensureServerActionAllowed(action, authentication);
        try {
            CommandResult result = servers.action(id, action, principal.getName());
            return ResponseEntity.ok(ActionResultView.from(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ActionResultView(false, e.getMessage(), "", e.getMessage()));
        }
    }

    @DeleteMapping("/servers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ActionResultView> deleteServer(@PathVariable Long id) {
        try {
            servers.delete(id);
            return ResponseEntity.ok(new ActionResultView(true, "Servidor eliminado de la lista.", "", ""));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ActionResultView(false, e.getMessage(), "", e.getMessage()));
        }
    }

    @GetMapping("/servers/{id}/rcon/config")
    @PreAuthorize("hasRole('ADMIN')")
    public RconConfigView rconConfig(@PathVariable Long id) {
        PalworldServer server = servers.get(id);
        return new RconConfigView(
                server.getId(),
                server.getName(),
                server.isRconEnabled(),
                server.getRconHost(),
                server.getRconPort(),
                server.getRconPassword() != null && !server.getRconPassword().isBlank()
        );
    }

    @GetMapping("/servers/{id}/auto-restart")
    @PreAuthorize("hasRole('ADMIN')")
    public AutoRestartConfigView autoRestartConfig(@PathVariable Long id) {
        PalworldServer server = servers.get(id);
        return new AutoRestartConfigView(
                server.getId(),
                server.getName(),
                server.isAutoRestartEnabled(),
                server.getAutoRestartTime(),
                15,
                server.getAutoRestartLastWarningDate() == null ? null : server.getAutoRestartLastWarningDate().toString(),
                server.getAutoRestartLastRunDate() == null ? null : server.getAutoRestartLastRunDate().toString()
        );
    }

    @GetMapping("/servers/{id}/player-analytics")
    public PlayerOnlineSnapshotService.PlayerDurationAnalytics playerAnalytics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "week") String range
    ) {
        return playerSnapshots.playerDurations(servers.get(id), range);
    }

    @GetMapping("/servers/{id}/player-registry")
    public PlayerPresenceService.PlayerRegistryView playerRegistry(
            @PathVariable Long id,
            @RequestParam(defaultValue = "week") String range
    ) {
        return playerPresence.registry(servers.get(id), range);
    }

    @GetMapping("/player-average")
    public PlayerPresenceService.PlayerAverageView playerAverage(@RequestParam(defaultValue = "day") String range) {
        return playerPresence.playerAverage(range);
    }

    @GetMapping("/servers/{id}/logs")
    public ServerLogsView serverLogs(@PathVariable Long id, @RequestParam(defaultValue = "200") int lines) {
        PalworldServer server = servers.get(id);
        int safeLines = Math.max(1, Math.min(lines, 1000));
        CommandResult result = servers.logs(id, rawLogLines(safeLines));
        String output = serverLogFilter.compactRconNoiseAndTail(result.stdout(), safeLines);
        String error = serverLogFilter.compactRconNoiseAndTail(result.stderr(), safeLines);
        List<InternalLogView> internalLogs = actionLogs.recent(server).stream()
                .map(InternalLogView::from)
                .toList();
        return new ServerLogsView(
                server.getId(),
                server.getName(),
                safeLines,
                result.success(),
                serverLogFilter.compactRconNoiseAndTail(result.combinedOutput(), safeLines),
                output,
                error,
                internalLogs
        );
    }

    @PutMapping("/servers/{id}/rcon/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RconConfigView> saveRconConfig(@PathVariable Long id, @RequestBody RconConfigRequest request) {
        try {
            servers.saveRconConfig(id, request.enabled(), request.host(), request.port(), request.password());
            return ResponseEntity.ok(rconConfig(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/servers/{id}/auto-restart")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutoRestartConfigView> saveAutoRestartConfig(@PathVariable Long id, @RequestBody AutoRestartConfigRequest request) {
        try {
            servers.saveAutoRestartConfig(id, request.enabled(), request.time());
            return ResponseEntity.ok(autoRestartConfig(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/activity")
    public PagedActivityView activity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ActivityLogView> logs = actionLogs.recentAll(Math.max(0, page), safeLogSize(size));
        return new PagedActivityView(
                logs.getContent().stream().map(this::activityView).toList(),
                new PageView(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages())
        );
    }

    private int safeLogSize(int logSize) {
        return switch (logSize) {
            case 10, 50, 100 -> logSize;
            default -> 10;
        };
    }

    private int rawLogLines(int visibleLines) {
        return Math.min(10000, Math.max(visibleLines, visibleLines * 10));
    }

    private ServerView serverView(ServerDashboardRow row) {
        PalworldServer server = row.server();
        ServerStatus status = row.status() == null ? ServerStatus.UNKNOWN : row.status();
        return new ServerView(
                server.getId(),
                server.getName(),
                displayType(server),
                server.getServiceName(),
                server.getContainerName(),
                server.getRootPath(),
                status.name(),
                status.getLabel(),
                server.getPublicPort(),
                server.isRconEnabled(),
                server.isRconEnabled() ? server.getRconPort() : null,
                server.isEnabled()
        );
    }

    private String displayType(PalworldServer server) {
        if (server.getType() == null) {
            return "Servidor";
        }
        return server.getType().isSystemd() ? "SYSTEMD" : "Servidor";
    }

    private ActivityView activityView(ActivityLogView log) {
        ActionStatus status = log.status();
        return new ActivityView(log.startedAt(), log.serverName(), log.action(), status == null ? null : status.name(), log.username());
    }

    private DashboardStatsView dashboardStats(List<ServerView> servers) {
        long running = servers.stream().filter(server -> "RUNNING".equals(server.status())).count();
        long stopped = servers.stream().filter(server -> "STOPPED".equals(server.status())).count();
        long errors = servers.stream().filter(server -> "ERROR".equals(server.status())).count();
        long rcon = servers.stream().filter(ServerView::rconEnabled).count();
        return new DashboardStatsView(servers.size(), running, stopped, errors, rcon);
    }

    private List<ActivityPointView> activitySeries() {
        return playerPresence.activitySeries(java.time.Duration.ofHours(24), 15).stream()
                .map(point -> new ActivityPointView(point.capturedAt(), point.playerCount(), point.players()))
                .toList();
    }

    private void ensureServerActionAllowed(String action, Authentication authentication) {
        if ("install".equals(action) && !isAdmin(authentication)) {
            throw new AccessDeniedException("Solo un administrador puede instalar o crear servidores.");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public record CsrfView(String token, String headerName, String parameterName) {
    }

    public record UserSessionView(
            String username,
            String displayName,
            String email,
            String role,
            List<String> roles,
            boolean enabled,
            boolean locked,
            boolean mustChangePassword
    ) {
    }

    public record DashboardView(
            List<ServerView> servers,
            DashboardStatsView stats,
            List<ActivityView> recentActivity,
            List<ActivityPointView> activitySeries,
            PageView page
    ) {
    }

    public record DashboardStatsView(long totalServers, long runningServers, long stoppedServers, long errorServers, long rconEnabledServers) {
    }

    public record ServerView(
            Long id,
            String name,
            String type,
            String serviceName,
            String containerName,
            String rootPath,
            String status,
            String statusLabel,
            Integer publicPort,
            boolean rconEnabled,
            Integer rconPort,
            boolean enabled
    ) {
    }

    public record ActivityView(LocalDateTime startedAt, String serverName, String action, String status, String username) {
    }

    public record ActivityPointView(String date, long actions, List<String> players) {
    }

    public record PageView(int page, int size, long totalElements, int totalPages) {
    }

    public record ActionResultView(boolean success, String message, String output, String error) {
        static ActionResultView from(CommandResult result) {
            String message = result.success() ? "Accion ejecutada." : "La accion fallo.";
            return new ActionResultView(result.success(), message, result.stdout(), result.stderr());
        }
    }

    public record RconConfigView(Long serverId, String serverName, boolean enabled, String host, Integer port, boolean passwordConfigured) {
    }

    public record RconConfigRequest(boolean enabled, String host, Integer port, String password) {
    }

    public record AutoRestartConfigView(
            Long serverId,
            String serverName,
            boolean enabled,
            String time,
            int warningMinutes,
            String lastWarningDate,
            String lastRunDate
    ) {
    }

    public record AutoRestartConfigRequest(boolean enabled, String time) {
    }

    public record PagedActivityView(List<ActivityView> items, PageView page) {
    }

    public record ServerLogsView(
            Long serverId,
            String serverName,
            int lines,
            boolean success,
            String combinedOutput,
            String output,
            String error,
            List<InternalLogView> internalLogs
    ) {
    }

    public record InternalLogView(
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String action,
            ActionStatus status,
            String message,
            String error,
            String username
    ) {
        static InternalLogView from(ServerActionLog log) {
            return new InternalLogView(
                    log.getStartedAt(),
                    log.getFinishedAt(),
                    log.getAction(),
                    log.getStatus(),
                    log.getMessage(),
                    log.getError(),
                    log.getUsername()
            );
        }
    }
}
