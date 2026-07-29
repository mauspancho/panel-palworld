package com.palworldadmin.app.service;

import com.palworldadmin.app.dto.ServerDashboardRow;
import com.palworldadmin.app.config.PalworldDefaultsProperties;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.entity.ServerType;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.service.server.PalworldPaths;
import com.palworldadmin.app.service.server.PalworldServerManager;
import com.palworldadmin.app.service.rcon.RconWelcomeMessageService;
import com.palworldadmin.app.util.CommandResult;
import com.palworldadmin.app.util.PathSecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class PalworldServerService {
    private final PalworldServerRepository servers;
    private final List<PalworldServerManager> managers;
    private final ActionLogService actionLogs;
    private final PlayerOnlineSnapshotService playerSnapshots;
    private final PlayerPresenceService playerPresence;
    private final PalworldDefaultsProperties defaults;
    private final RconWelcomeMessageService welcomeMessages;

    public PalworldServerService(PalworldServerRepository servers, List<PalworldServerManager> managers, ActionLogService actionLogs, PlayerOnlineSnapshotService playerSnapshots, PlayerPresenceService playerPresence, PalworldDefaultsProperties defaults, RconWelcomeMessageService welcomeMessages) {
        this.servers = servers;
        this.managers = managers;
        this.actionLogs = actionLogs;
        this.playerSnapshots = playerSnapshots;
        this.playerPresence = playerPresence;
        this.defaults = defaults;
        this.welcomeMessages = welcomeMessages;
    }

    public List<ServerDashboardRow> dashboardRows() {
        return servers.findAllByOrderByNameAsc().stream()
                .map(server -> new ServerDashboardRow(server, status(server)))
                .toList();
    }

    public List<PalworldServer> findAll() {
        return servers.findAllByOrderByNameAsc();
    }

    public PalworldServer get(Long id) {
        return servers.findById(id).orElseThrow(() -> new IllegalArgumentException("Servidor no encontrado."));
    }

    @Transactional
    public PalworldServer save(PalworldServer server) {
        preserveRconConfig(server);
        normalizeAndValidate(server);
        return servers.save(server);
    }

    @Transactional
    public PalworldServer saveRconConfig(Long id, boolean enabled, String host, Integer port, String password) {
        PalworldServer server = get(id);
        server.setRconEnabled(enabled);
        server.setRconHost(normalizeRconHost(host));
        server.setRconPort(normalizeRconPort(port));
        if (password != null && !password.isBlank()) {
            server.setRconPassword(password.trim());
        }
        if (!server.isRconEnabled()) {
            server.setRconPassword(password == null || password.isBlank() ? server.getRconPassword() : password.trim());
        }
        return servers.save(server);
    }

    @Transactional
    public PalworldServer saveAutoRestartConfig(Long id, boolean enabled, String time) {
        PalworldServer server = get(id);
        server.setAutoRestartEnabled(enabled);
        server.setAutoRestartTime(enabled ? normalizeAutoRestartTime(time) : normalizeOptionalAutoRestartTime(time));
        return servers.save(server);
    }

    @Transactional
    public void markAutoRestartWarningSent(Long id, LocalDate restartDate) {
        PalworldServer server = get(id);
        server.setAutoRestartLastWarningDate(restartDate);
        servers.save(server);
    }

    @Transactional
    public void markAutoRestartRan(Long id, LocalDate restartDate) {
        PalworldServer server = get(id);
        server.setAutoRestartLastRunDate(restartDate);
        servers.save(server);
    }

    @Transactional
    public void delete(Long id) {
        PalworldServer server = get(id);
        playerSnapshots.deleteForServer(server);
        playerPresence.deleteForServer(server);
        actionLogs.deleteForServer(server);
        welcomeMessages.deleteForServer(server);
        servers.delete(server);
    }

    public ServerStatus status(PalworldServer server) {
        try {
            return manager(server).status(server);
        } catch (Exception e) {
            return ServerStatus.UNKNOWN;
        }
    }

    public CommandResult action(Long serverId, String action, String username) {
        PalworldServer server = get(serverId);
        var log = actionLogs.started(server, action, username);
        try {
            CommandResult result = switch (action) {
                case "start" -> manager(server).start(server);
                case "stop" -> manager(server).stop(server);
                case "restart" -> manager(server).restart(server);
                case "update" -> manager(server).update(server);
                case "install" -> manager(server).install(server);
                case "fix-permissions" -> manager(server).fixPermissions(server);
                default -> throw new IllegalArgumentException("AcciÃ³n no permitida.");
            };
            actionLogs.finish(log, result);
            return result;
        } catch (Exception e) {
            actionLogs.fail(log, e);
            throw e;
        }
    }

    public CommandResult logs(Long serverId, int lines) {
        PalworldServer server = get(serverId);
        return manager(server).logs(server, lines);
    }

    public PathValidation paths(Long serverId) {
        PalworldServer server = get(serverId);
        PalworldPaths paths = PalworldPaths.from(server);
        return new PathValidation(
                paths,
                Files.exists(paths.root()),
                Files.exists(paths.settingsFile()),
                Files.exists(paths.defaultSettingsFile()),
                Files.exists(paths.saveGamesRoot())
        );
    }

    private PalworldServerManager manager(PalworldServer server) {
        return managers.stream()
                .filter(candidate -> candidate.supports(server))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de servidor sin manager."));
    }

    private void normalizeAndValidate(PalworldServer server) {
        server.setRootPath(PathSecurityUtil.normalizeRoot(server).toString());
        if (server.getSteamcmdPath() == null || server.getSteamcmdPath().isBlank()) {
            server.setSteamcmdPath(defaults.getSteamcmdPath());
        }
        if (server.getLinuxUser() == null || server.getLinuxUser().isBlank()) {
            server.setLinuxUser(defaults.getRunUser());
        }
        if (server.getLinuxGroup() == null || server.getLinuxGroup().isBlank()) {
            server.setLinuxGroup(defaults.getRunGroup());
        }
        if (server.getPublicPort() == null) {
            server.setPublicPort(defaults.getPublicPort());
        }
        server.setRconHost(normalizeRconHost(server.getRconHost()));
        server.setRconPort(normalizeRconPort(server.getRconPort()));
        if (server.getRconPassword() != null) {
            server.setRconPassword(server.getRconPassword().trim());
        }
        server.setLinuxUser(PathSecurityUtil.requireSafeLinuxUser(server.getLinuxUser(), "Usuario"));
        server.setLinuxGroup(PathSecurityUtil.requireSafeLinuxUser(server.getLinuxGroup(), "Grupo"));
        if (server.getType() != null && server.getType().isSystemd()) {
            server.setType(ServerType.SYSTEMD);
            server.setServiceName(PathSecurityUtil.requireSafeUnitName(server.getServiceName()));
            server.setContainerName(null);
            server.setComposeProjectName(null);
        } else {
            server.setContainerName(PathSecurityUtil.requireSafeContainerName(server.getContainerName()));
            server.setComposeProjectName(PathSecurityUtil.requireSafeComposeProject(server.getComposeProjectName()));
            server.setServiceName(null);
        }
    }

    private void preserveRconConfig(PalworldServer server) {
        if (server.getId() == null) {
            return;
        }
        servers.findById(server.getId()).ifPresent(existing -> {
            server.setRconEnabled(existing.isRconEnabled());
            server.setRconHost(existing.getRconHost());
            server.setRconPort(existing.getRconPort());
            server.setRconPassword(existing.getRconPassword());
            server.setAutoRestartEnabled(existing.isAutoRestartEnabled());
            server.setAutoRestartTime(existing.getAutoRestartTime());
            server.setAutoRestartLastWarningDate(existing.getAutoRestartLastWarningDate());
            server.setAutoRestartLastRunDate(existing.getAutoRestartLastRunDate());
        });
    }

    private String normalizeRconHost(String host) {
        if (host == null || host.isBlank()) {
            return "127.0.0.1";
        }
        String trimmed = host.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Host RCON demasiado largo.");
        }
        if (!trimmed.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Host RCON invalido.");
        }
        return trimmed;
    }

    private Integer normalizeRconPort(Integer port) {
        if (port == null) {
            return 25575;
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Puerto RCON invalido.");
        }
        return port;
    }

    private String normalizeAutoRestartTime(String time) {
        if (time == null || time.isBlank()) {
            throw new IllegalArgumentException("La hora de reinicio automatico es obligatoria.");
        }
        return parseAutoRestartTime(time);
    }

    private String normalizeOptionalAutoRestartTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        return parseAutoRestartTime(time);
    }

    private String parseAutoRestartTime(String time) {
        String trimmed = time.trim();
        try {
            return LocalTime.parse(trimmed).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La hora debe tener formato HH:mm.");
        }
    }

    public record PathValidation(
            PalworldPaths paths,
            boolean rootExists,
            boolean settingsExists,
            boolean defaultSettingsExists,
            boolean saveGamesExists
    ) {
    }
}
