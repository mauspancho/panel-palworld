package com.palworldadmin.app.service;

import com.palworldadmin.app.dto.ServerDashboardRow;
import com.palworldadmin.app.config.PalworldDefaultsProperties;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.entity.ServerType;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.service.server.PalworldPaths;
import com.palworldadmin.app.service.server.PalworldServerManager;
import com.palworldadmin.app.util.CommandResult;
import com.palworldadmin.app.util.PathSecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.util.List;

@Service
public class PalworldServerService {
    private final PalworldServerRepository servers;
    private final List<PalworldServerManager> managers;
    private final ActionLogService actionLogs;
    private final PalworldDefaultsProperties defaults;

    public PalworldServerService(PalworldServerRepository servers, List<PalworldServerManager> managers, ActionLogService actionLogs, PalworldDefaultsProperties defaults) {
        this.servers = servers;
        this.managers = managers;
        this.actionLogs = actionLogs;
        this.defaults = defaults;
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
        normalizeAndValidate(server);
        return servers.save(server);
    }

    @Transactional
    public void delete(Long id) {
        PalworldServer server = get(id);
        actionLogs.deleteForServer(server);
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
        server.setLinuxUser(PathSecurityUtil.requireSafeLinuxUser(server.getLinuxUser(), "Usuario"));
        server.setLinuxGroup(PathSecurityUtil.requireSafeLinuxUser(server.getLinuxGroup(), "Grupo"));
        if (server.getType() == ServerType.SYSTEMD) {
            server.setServiceName(PathSecurityUtil.requireSafeUnitName(server.getServiceName()));
            server.setContainerName(null);
            server.setComposeProjectName(null);
        } else {
            server.setContainerName(PathSecurityUtil.requireSafeContainerName(server.getContainerName()));
            server.setComposeProjectName(PathSecurityUtil.requireSafeComposeProject(server.getComposeProjectName()));
            server.setServiceName(null);
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
