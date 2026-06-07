package com.palworldadmin.app.util;

import com.palworldadmin.app.entity.PalworldServer;

import java.nio.file.Path;

public final class PathSecurityUtil {
    private PathSecurityUtil() {
    }

    public static Path normalizeRoot(PalworldServer server) {
        return validateRootPath(server.getRootPath());
    }

    public static Path requireInsideRoot(PalworldServer server, Path candidate) {
        Path root = normalizeRoot(server);
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("La ruta solicitada estÃ¡ fuera del SERVER_ROOT registrado.");
        }
        return normalized;
    }

    public static String requireSafeUnitName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nombre de servicio systemd invÃ¡lido.");
        }
        String normalized = name.trim();
        if (!normalized.endsWith(".service")) {
            normalized = normalized + ".service";
        }
        String baseName = normalized.substring(0, normalized.length() - ".service".length());
        if (!baseName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Nombre de servicio systemd invÃ¡lido.");
        }
        return normalized;
    }

    public static String requireSafeContainerName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Nombre de contenedor Docker invÃ¡lido.");
        }
        return name;
    }

    public static String requireSafeComposeProject(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Nombre de proyecto Docker Compose invÃ¡lido.");
        }
        return name;
    }

    public static Path validateRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("La ruta raÃ­z del servidor es obligatoria.");
        }
        Path root = Path.of(rootPath).toAbsolutePath().normalize();
        if (!root.isAbsolute() || root.toString().contains("..")) {
            throw new IllegalArgumentException("La ruta raÃ­z debe ser absoluta y no puede contener path traversal.");
        }
        String normalized = root.toString().replace('\\', '/');
        if (normalized.equals("/") || normalized.equals("/home") || normalized.equals("/opt")) {
            throw new IllegalArgumentException("La ruta raÃ­z debe apuntar a una carpeta especÃ­fica del servidor.");
        }
        return root;
    }

    public static String requireSafeLinuxUser(String value, String label) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException(label + " Linux invÃ¡lido.");
        }
        return value;
    }
}
