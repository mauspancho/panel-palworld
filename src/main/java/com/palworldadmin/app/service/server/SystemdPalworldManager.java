package com.palworldadmin.app.service.server;

import com.palworldadmin.app.config.AdminProperties;
import com.palworldadmin.app.config.PalworldDefaultsProperties;
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
import java.util.List;

@Service
public class SystemdPalworldManager implements PalworldServerManager {
    private final CommandExecutor commands;
    private final AdminProperties properties;
    private final PalworldDefaultsProperties defaults;

    public SystemdPalworldManager(CommandExecutor commands, AdminProperties properties, PalworldDefaultsProperties defaults) {
        this.commands = commands;
        this.properties = properties;
        this.defaults = defaults;
    }

    @Override
    public boolean supports(PalworldServer server) {
        return server.getType() != null && server.getType().isSystemd();
    }

    @Override
    public ServerStatus status(PalworldServer server) {
        String service = PathSecurityUtil.requireSafeUnitName(server.getServiceName());
        CommandResult result = commands.execute(List.of(properties.getSystemctlCommand(), "is-active", service), normalTimeout());
        String state = result.stdout().trim().toLowerCase();
        if ("active".equals(state)) {
            return ServerStatus.RUNNING;
        }
        if ("inactive".equals(state)) {
            return ServerStatus.STOPPED;
        }
        if ("activating".equals(state) || "deactivating".equals(state)) {
            return ServerStatus.RESTARTING;
        }
        if ("failed".equals(state)) {
            return ServerStatus.ERROR;
        }
        return ServerStatus.UNKNOWN;
    }

    @Override
    public CommandResult start(PalworldServer server) {
        return systemctl("start", server);
    }

    @Override
    public CommandResult stop(PalworldServer server) {
        return systemctl("stop", server);
    }

    @Override
    public CommandResult restart(PalworldServer server) {
        return systemctl("restart", server);
    }

    @Override
    public CommandResult logs(PalworldServer server, int lines) {
        String service = PathSecurityUtil.requireSafeUnitName(server.getServiceName());
        return commands.execute(List.of(properties.getJournalctlCommand(), "-u", service, "-n", String.valueOf(Math.max(1, Math.min(lines, 10000))), "--no-pager"), normalTimeout());
    }

    @Override
    public CommandResult update(PalworldServer server) {
        CommandResult userCheck = validateLinuxAccount(server);
        if (!userCheck.success()) {
            return userCheck;
        }
        CommandResult stop = stop(server);
        if (!stop.success()) {
            return stop;
        }
        CommandResult update = runSteamUpdateScript(server);
        CommandResult permissions = fixPermissions(server);
        CommandResult start = start(server);
        return new CommandResult(
                List.of("systemd update", server.getServiceName()),
                update.success() && permissions.success() && start.success() ? 0 : 1,
                userCheck.stdout() + stop.stdout() + update.stdout() + permissions.stdout() + start.stdout(),
                userCheck.stderr() + stop.stderr() + update.stderr() + permissions.stderr() + start.stderr(),
                userCheck.duration().plus(stop.duration()).plus(update.duration()).plus(permissions.duration()).plus(start.duration()),
                userCheck.timedOut() || stop.timedOut() || update.timedOut() || permissions.timedOut() || start.timedOut()
        );
    }

    @Override
    public CommandResult install(PalworldServer server) {
        try {
            String service = PathSecurityUtil.requireSafeUnitName(server.getServiceName());
            Path root = PathSecurityUtil.normalizeRoot(server);
            Files.createDirectories(root);
            CommandResult userCheck = validateLinuxAccount(server);
            if (!userCheck.success()) {
                return userCheck;
            }

            Path localUnit = PathSecurityUtil.requireInsideRoot(server, root.resolve(service));
            Files.writeString(localUnit, systemdUnit(server, root), StandardCharsets.UTF_8);

            CommandResult copy = commands.execute(sudo(properties.getCpCommand(), localUnit.toString(), "/etc/systemd/system/" + service), normalTimeout());
            if (!copy.success()) {
                return failedStep("No se pudo copiar el archivo service a /etc/systemd/system. Revisa sudoers para permitir este comando sin password.", copy);
            }
            CommandResult reload = commands.execute(sudo(properties.getSystemctlCommand(), "daemon-reload"), normalTimeout());
            if (!reload.success()) {
                return failedStep("No se pudo recargar systemd. Revisa sudoers para systemctl daemon-reload.", reload);
            }
            CommandResult enable = commands.execute(sudo(properties.getSystemctlCommand(), "enable", service), normalTimeout());
            if (!enable.success()) {
                return failedStep("No se pudo habilitar el servicio systemd. Revisa sudoers para systemctl enable.", enable);
            }

            CommandResult steam = runSteamUpdateScript(server);
            if (!steam.success()) {
                return failedStep("SteamCMD no pudo instalar o validar el servidor Palworld.", steam);
            }
            CommandResult permissions = fixPermissions(server);
            if (!permissions.success()) {
                return failedStep("No se pudieron ajustar permisos del servidor. Revisa sudoers para chown/chmod y que el usuario/grupo Linux existan.", permissions);
            }

            CommandResult start = commands.execute(sudo(properties.getSystemctlCommand(), "start", service), normalTimeout());
            if (!start.success()) {
                return failedStep("El servidor se instalo, pero systemd no pudo iniciarlo. Revisa journalctl para este servicio.", start);
            }
            return merge("systemd install", userCheck, copy, reload, enable, steam, permissions, start);
        } catch (IOException | RuntimeException e) {
            return new CommandResult(List.of("systemd install", server.getServiceName()), 1, "", e.getMessage(), Duration.ZERO, false);
        }
    }

    @Override
    public CommandResult fixPermissions(PalworldServer server) {
        Path root = PathSecurityUtil.normalizeRoot(server);
        String linuxUser = PathSecurityUtil.requireSafeLinuxUser(effectiveLinuxUser(server), "Usuario");
        String linuxGroup = PathSecurityUtil.requireSafeLinuxUser(effectiveLinuxGroup(server), "Grupo");
        CommandResult chown = commands.execute(sudo(properties.getChownCommand(), "-R", linuxUser + ":" + linuxGroup, root.toString()), normalTimeout());
        if (!chown.success()) {
            return chown;
        }
        CommandResult chmod = commands.execute(sudo(properties.getChmodCommand(), "+x", root.resolve("PalServer.sh").toString()), normalTimeout());
        return merge("fix permissions", chown, chmod);
    }

    private CommandResult runSteamUpdateScript(PalworldServer server) {
        try {
            String steamcmd = requireSteamcmd(server);
            Path root = PathSecurityUtil.normalizeRoot(server);
            Files.createDirectories(root);
            Path script = PathSecurityUtil.requireInsideRoot(server, root.resolve("update-palworld.sh"));
            Files.writeString(script, updateScript(steamcmd, root), StandardCharsets.UTF_8);

            CommandResult chmod = commands.execute(List.of("chmod", "+x", script.toString()), normalTimeout());
            if (!chmod.success()) {
                return failedStep("No se pudo dar permiso de ejecucion al script de actualizacion: " + script, chmod);
            }

            String linuxUser = PathSecurityUtil.requireSafeLinuxUser(effectiveLinuxUser(server), "Usuario");
            CommandResult update = commands.execute(sudo("-u", linuxUser, "/bin/bash", script.toString()), Duration.ofSeconds(properties.getUpdateTimeoutSeconds()));
            return new CommandResult(
                    List.of("sudo", "-u", linuxUser, "/bin/bash", script.toString()),
                    update.exitCode(),
                    "Script generado: " + script + System.lineSeparator() + chmod.stdout() + update.stdout(),
                    chmod.stderr() + update.stderr(),
                    chmod.duration().plus(update.duration()),
                    chmod.timedOut() || update.timedOut()
            );
        } catch (IOException | RuntimeException e) {
            return new CommandResult(List.of("systemd update script", server.getServiceName()), 1, "", e.getMessage(), Duration.ZERO, false);
        }
    }

    private String updateScript(String steamcmd, Path root) {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                export SteamAppId=2394010
                STEAMCMD=%s
                INSTALL_DIR=%s

                echo "Actualizando Palworld Dedicated Server"
                echo "SteamCMD: ${STEAMCMD}"
                echo "Ruta servidor: ${INSTALL_DIR}"

                "${STEAMCMD}" +force_install_dir "${INSTALL_DIR}" +login anonymous +app_update 2394010 validate +quit
                """.formatted(shellQuote(steamcmd), shellQuote(root.toString()));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private CommandResult systemctl(String action, PalworldServer server) {
        String service = PathSecurityUtil.requireSafeUnitName(server.getServiceName());
        return commands.execute(sudo(properties.getSystemctlCommand(), action, service), normalTimeout());
    }

    private List<String> sudo(String... args) {
        List<String> command = new java.util.ArrayList<>(properties.sudoCommandParts());
        command.addAll(List.of(args));
        return command;
    }

    private String requireSteamcmd(PalworldServer server) {
        if (server.getSteamcmdPath() == null || server.getSteamcmdPath().isBlank()) {
            throw new IllegalArgumentException("La ruta de SteamCMD es obligatoria para instalar servidores systemd.");
        }
        return server.getSteamcmdPath().trim();
    }

    private String systemdUnit(PalworldServer server, Path root) {
        String executable = root.resolve("PalServer.sh").toString();
        return """
                [Unit]
                Description=Palworld Dedicated Server - %s
                Wants=network-online.target
                After=network-online.target

                [Service]
                Type=simple
                User=%s
                Group=%s
                WorkingDirectory=%s
                Environment=SteamAppId=2394010
                ExecStart=/bin/bash %s %s
                Restart=on-failure
                RestartSec=10
                LimitNOFILE=100000

                [Install]
                WantedBy=multi-user.target
                """.formatted(server.getName(), effectiveLinuxUser(server), effectiveLinuxGroup(server), root, executable, launchArgs(server));
    }

    private String launchArgs(PalworldServer server) {
        List<String> args = new java.util.ArrayList<>();
        if (defaults.isPublicLobby()) {
            args.add("-publiclobby");
        }
        Integer port = server.getPublicPort() == null ? defaults.getPublicPort() : server.getPublicPort();
        if (port != null) {
            args.add("-publicport=" + port);
        }
        if (defaults.isUsePerfThreads()) {
            args.add("-useperfthreads");
        }
        return String.join(" ", args);
    }

    private CommandResult validateLinuxAccount(PalworldServer server) {
        String linuxUser = PathSecurityUtil.requireSafeLinuxUser(effectiveLinuxUser(server), "Usuario");
        String linuxGroup = PathSecurityUtil.requireSafeLinuxUser(effectiveLinuxGroup(server), "Grupo");
        CommandResult user = commands.execute(List.of("id", linuxUser), normalTimeout());
        if (!user.success()) {
            return new CommandResult(List.of("id", linuxUser), 1, user.stdout(), "El usuario Linux configurado no existe. Crea el usuario o selecciona otro.", user.duration(), user.timedOut());
        }
        CommandResult group = commands.execute(List.of("getent", "group", linuxGroup), normalTimeout());
        if (!group.success()) {
            return new CommandResult(List.of("getent", "group", linuxGroup), 1, group.stdout(), "El grupo Linux configurado no existe. Crea el grupo o selecciona otro.", group.duration(), group.timedOut());
        }
        return merge("validate linux account", user, group);
    }

    private CommandResult failedStep(String message, CommandResult result) {
        StringBuilder stderr = new StringBuilder(message)
                .append(System.lineSeparator())
                .append("Comando: ")
                .append(String.join(" ", result.command()));
        String output = result.combinedOutput();
        if (!output.isBlank()) {
            stderr.append(System.lineSeparator()).append(output);
        }
        return new CommandResult(result.command(), result.exitCode() == 0 ? 1 : result.exitCode(), "", stderr.toString(), result.duration(), result.timedOut());
    }

    private String effectiveLinuxUser(PalworldServer server) {
        return server.getLinuxUser() == null || server.getLinuxUser().isBlank() ? defaults.getRunUser() : server.getLinuxUser();
    }

    private String effectiveLinuxGroup(PalworldServer server) {
        return server.getLinuxGroup() == null || server.getLinuxGroup().isBlank() ? defaults.getRunGroup() : server.getLinuxGroup();
    }

    private CommandResult merge(String label, CommandResult... results) {
        int exitCode = 0;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Duration duration = Duration.ZERO;
        boolean timedOut = false;
        for (CommandResult result : results) {
            if (!result.success()) {
                exitCode = result.exitCode() == 0 ? 1 : result.exitCode();
            }
            stdout.append(result.stdout());
            stderr.append(result.stderr());
            duration = duration.plus(result.duration());
            timedOut = timedOut || result.timedOut();
        }
        return new CommandResult(List.of(label), exitCode, stdout.toString(), stderr.toString(), duration, timedOut);
    }

    private Duration normalTimeout() {
        return Duration.ofSeconds(properties.getCommandTimeoutSeconds());
    }
}
