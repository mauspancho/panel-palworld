package com.palworldadmin.app.service.backup;

import com.palworldadmin.app.dto.BackupEntryView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.server.PalworldPaths;
import com.palworldadmin.app.util.CommandResult;
import com.palworldadmin.app.util.PathSecurityUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PalworldServerService servers;
    private final ActionLogService actionLogs;
    public BackupService(PalworldServerService servers, ActionLogService actionLogs) {
        this.servers = servers;
        this.actionLogs = actionLogs;
    }

    public List<BackupEntryView> list(Long serverId) throws IOException {
        PalworldServer server = servers.get(serverId);
        PalworldPaths paths = PalworldPaths.from(server);
        if (!Files.exists(paths.saveGamesRoot())) {
            return List.of();
        }
        try (Stream<Path> worlds = Files.list(paths.saveGamesRoot())) {
            return worlds.filter(Files::isDirectory)
                    .flatMap(world -> scanWorldBackups(server, paths, world).stream())
                    .sorted(Comparator.comparing(BackupEntryView::detectedDate).reversed())
                    .toList();
        }
    }

    public List<String> content(Long serverId, String backupPath) throws IOException {
        PalworldServer server = servers.get(serverId);
        Path backup = requireBackupPath(server, backupPath);
        try (Stream<Path> stream = Files.walk(backup, 2)) {
            return stream
                    .filter(path -> !path.equals(backup))
                    .limit(200)
                    .map(path -> backup.relativize(path).toString())
                    .toList();
        }
    }

    public void zip(Long serverId, String backupPath, OutputStream output) throws IOException {
        PalworldServer server = servers.get(serverId);
        Path backup = requireBackupPath(server, backupPath);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zipDirectory(backup, backup.getFileName().toString(), zip);
        }
    }

    public void restore(Long serverId, String backupPath, boolean startAfterRestore, String username) throws IOException {
        PalworldServer server = servers.get(serverId);
        var log = actionLogs.started(server, "restore", username);
        try {
            Path backup = requireBackupPath(server, backupPath);
            Path world = backup.getParent().getParent();
            Path preRestore = world.resolve("pre-restore-" + BACKUP_FORMAT.format(LocalDateTime.now()));
            servers.action(serverId, "stop", username);
            copyDirectory(world, preRestore, path -> !path.startsWith(preRestore) && !path.getFileName().toString().startsWith("pre-restore-"));
            copyDirectory(backup, world, path -> true);
            servers.action(serverId, "fix-permissions", username);
            if (startAfterRestore) {
                servers.action(serverId, "start", username);
            }
            actionLogs.finish(log, new CommandResult(List.of("restore", backup.toString()), 0, "Backup restaurado. Respaldo previo: " + preRestore, "", Duration.ZERO, false));
        } catch (IOException | RuntimeException e) {
            actionLogs.fail(log, e);
            throw e;
        }
    }

    private List<BackupEntryView> scanWorldBackups(PalworldServer server, PalworldPaths paths, Path world) {
        Path backupRoot = world.resolve("backup");
        if (!Files.exists(backupRoot)) {
            return List.of();
        }
        try (Stream<Path> backups = Files.walk(backupRoot, 3)) {
            return backups.filter(Files::isDirectory)
                    .filter(path -> !path.equals(backupRoot))
                    .filter(path -> containsFiles(path))
                    .map(path -> toEntry(server, paths, world, path))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private BackupEntryView toEntry(PalworldServer server, PalworldPaths paths, Path world, Path path) {
        PathSecurityUtil.requireInsideRoot(server, path);
        return new BackupEntryView(
                world.getFileName().toString(),
                paths.root().relativize(path).toString(),
                path.toString(),
                modified(path),
                size(path)
        );
    }

    private Path requireBackupPath(PalworldServer server, String backupPath) {
        PalworldPaths paths = PalworldPaths.from(server);
        Path requested = Path.of(backupPath);
        Path absolute = requested.isAbsolute() ? requested : paths.root().resolve(requested);
        Path normalized = PathSecurityUtil.requireInsideRoot(server, absolute);
        if (!normalized.toString().contains(Path.of("SaveGames", "0").toString()) || !normalized.toString().contains("backup")) {
            throw new IllegalArgumentException("La ruta no corresponde a un backup de Palworld.");
        }
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("El backup seleccionado no existe.");
        }
        return normalized;
    }

    private boolean containsFiles(Path directory) {
        try (Stream<Path> children = Files.walk(directory, 2)) {
            return children.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
        }
    }

    private long size(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile).mapToLong(this::fileSize).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private LocalDateTime modified(Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.MIN;
        }
    }

    private void copyDirectory(Path source, Path target, java.util.function.Predicate<Path> include) throws IOException {
        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!include.test(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (include.test(file)) {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void zipDirectory(Path directory, String rootName, ZipOutputStream zip) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                ZipEntry entry = new ZipEntry(rootName + "/" + directory.relativize(path).toString().replace('\\', '/'));
                zip.putNextEntry(entry);
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }
}
