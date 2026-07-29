package com.palworldadmin.app.service.server;

import com.palworldadmin.app.config.AdminProperties;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.entity.ServerType;
import com.palworldadmin.app.util.CommandExecutor;
import com.palworldadmin.app.util.CommandResult;
import com.palworldadmin.app.util.PathSecurityUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class DockerPalworldManager implements PalworldServerManager {
    private final CommandExecutor commands;
    private final AdminProperties properties;

    public DockerPalworldManager(CommandExecutor commands, AdminProperties properties) {
        this.commands = commands;
        this.properties = properties;
    }

    @Override
    public boolean supports(PalworldServer server) {
        return server.getType() == ServerType.DOCKER;
    }

    @Override
    public ServerStatus status(PalworldServer server) {
        String container = PathSecurityUtil.requireSafeContainerName(server.getContainerName());
        CommandResult result = commands.execute(List.of("docker", "inspect", "-f", "{{.State.Status}}", container), normalTimeout());
        if (!result.success()) {
            return ServerStatus.UNKNOWN;
        }
        String state = result.stdout().trim().toLowerCase();
        return switch (state) {
            case "running" -> ServerStatus.RUNNING;
            case "exited", "created", "paused" -> ServerStatus.STOPPED;
            case "restarting" -> ServerStatus.RESTARTING;
            case "dead" -> ServerStatus.ERROR;
            default -> ServerStatus.UNKNOWN;
        };
    }

    @Override
    public CommandResult start(PalworldServer server) {
        return dockerContainerCommand("start", server);
    }

    @Override
    public CommandResult stop(PalworldServer server) {
        return dockerContainerCommand("stop", server);
    }

    @Override
    public CommandResult restart(PalworldServer server) {
        return dockerContainerCommand("restart", server);
    }

    @Override
    public CommandResult logs(PalworldServer server, int lines) {
        String container = PathSecurityUtil.requireSafeContainerName(server.getContainerName());
        return commands.execute(List.of("docker", "logs", "--tail", String.valueOf(Math.max(1, Math.min(lines, 10000))), container), normalTimeout());
    }

    @Override
    public CommandResult update(PalworldServer server) {
        String updateCommand = server.getUpdateCommand();
        if (updateCommand == null || updateCommand.isBlank()) {
            return new CommandResult(List.of(), 1, "", "La actualizacion Docker debe configurarse segun la imagen usada.", Duration.ZERO, false);
        }
        return executeValidatedUpdate(server, updateCommand.trim());
    }

    @Override
    public CommandResult install(PalworldServer server) {
        try {
            String container = PathSecurityUtil.requireSafeContainerName(server.getContainerName());
            String project = server.getComposeProjectName() == null || server.getComposeProjectName().isBlank()
                    ? container
                    : PathSecurityUtil.requireSafeComposeProject(server.getComposeProjectName());
            Path root = PathSecurityUtil.normalizeRoot(server);
            Files.createDirectories(root);
            Path compose = PathSecurityUtil.requireInsideRoot(server, root.resolve("docker-compose.yml"));
            Files.writeString(compose, dockerCompose(container), StandardCharsets.UTF_8);
            return commands.execute(List.of("docker", "compose", "-p", project, "-f", compose.toString(), "up", "-d"), updateTimeout());
        } catch (IOException | RuntimeException e) {
            return new CommandResult(List.of("docker compose install", server.getContainerName()), 1, "", e.getMessage(), Duration.ZERO, false);
        }
    }

    @Override
    public CommandResult fixPermissions(PalworldServer server) {
        return new CommandResult(List.of("docker fix permissions"), 0, "No aplica correcciÃ³n de permisos systemd para Docker.", "", Duration.ZERO, false);
    }

    private CommandResult dockerContainerCommand(String action, PalworldServer server) {
        String container = PathSecurityUtil.requireSafeContainerName(server.getContainerName());
        return commands.execute(List.of("docker", action, container), normalTimeout());
    }

    private CommandResult executeValidatedUpdate(PalworldServer server, String updateCommand) {
        String container = PathSecurityUtil.requireSafeContainerName(server.getContainerName());
        if (updateCommand.matches("^docker exec " + Pattern.quote(container) + " /[A-Za-z0-9_./-]+( [A-Za-z0-9_./:=,@+-]+)*$")) {
            return commands.execute(split(updateCommand), updateTimeout());
        }
        String project = PathSecurityUtil.requireSafeComposeProject(server.getComposeProjectName());
        if (project != null && updateCommand.equals("docker compose -p " + project + " pull && docker compose -p " + project + " up -d")) {
            CommandResult pull = commands.execute(List.of("docker", "compose", "-p", project, "pull"), updateTimeout());
            if (!pull.success()) {
                return pull;
            }
            CommandResult up = commands.execute(List.of("docker", "compose", "-p", project, "up", "-d"), updateTimeout());
            return new CommandResult(List.of("docker compose update", project), up.exitCode(), pull.stdout() + up.stdout(), pull.stderr() + up.stderr(), pull.duration().plus(up.duration()), up.timedOut());
        }
        return new CommandResult(List.of(), 1, "", "Comando de actualizacion Docker no permitido. Use docker exec <contenedor> /ruta/update.sh o docker compose -p <project> pull && docker compose -p <project> up -d.", Duration.ZERO, false);
    }

    private List<String> split(String command) {
        return new ArrayList<>(List.of(command.split(" ")));
    }

    private String dockerCompose(String container) {
        return """
                services:
                  palworld:
                    image: thijsvanloef/palworld-server-docker:latest
                    container_name: %s
                    restart: unless-stopped
                    ports:
                      - "8211:8211/udp"
                      - "27015:27015/udp"
                      - "25575:25575/tcp"
                      - "8212:8212/tcp"
                    environment:
                      PUID: "1000"
                      PGID: "1000"
                      PORT: "8211"
                      PLAYERS: "32"
                      MULTITHREADING: "true"
                      COMMUNITY: "false"
                      RCON_ENABLED: "true"
                      RCON_PORT: "25575"
                      ADMIN_PASSWORD: "change-me"
                    volumes:
                      - ./data:/palworld
                """.formatted(container);
    }

    private Duration normalTimeout() {
        return Duration.ofSeconds(properties.getCommandTimeoutSeconds());
    }

    private Duration updateTimeout() {
        return Duration.ofSeconds(properties.getUpdateTimeoutSeconds());
    }
}
