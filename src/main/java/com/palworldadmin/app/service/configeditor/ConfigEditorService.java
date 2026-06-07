package com.palworldadmin.app.service.configeditor;

import com.palworldadmin.app.dto.ConfigField;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.server.PalworldPaths;
import com.palworldadmin.app.util.IniParser;
import com.palworldadmin.app.util.PathSecurityUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigEditorService {
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> COMMON_FIELDS = List.of(
            "ServerName",
            "ServerDescription",
            "AdminPassword",
            "ServerPassword",
            "ServerPlayerMaxNum",
            "PublicIP",
            "PublicPort",
            "RCONEnabled",
            "RCONPort",
            "RESTAPIEnabled",
            "RESTAPIPort",
            "bIsUseBackupSaveData",
            "ExpRate",
            "PalCaptureRate",
            "PalSpawnNumRate",
            "CollectionDropRate",
            "EnemyDropItemRate",
            "DeathPenalty",
            "DayTimeSpeedRate",
            "NightTimeSpeedRate"
    );

    private final PalworldServerService servers;

    public ConfigEditorService(PalworldServerService servers) {
        this.servers = servers;
    }

    public ConfigView load(Long serverId) throws IOException {
        PalworldServer server = servers.get(serverId);
        PalworldPaths paths = PalworldPaths.from(server);
        String content = Files.exists(paths.settingsFile()) ? Files.readString(paths.settingsFile(), StandardCharsets.UTF_8) : "";
        Map<String, String> parsed = IniParser.parseOptionSettings(content);
        List<ConfigField> fields = formFields(parsed);
        return new ConfigView(server, paths, content, fields, Files.exists(paths.settingsFile()), Files.exists(paths.defaultSettingsFile()), servers.status(server).name());
    }

    public void copyDefault(Long serverId) throws IOException {
        PalworldServer server = servers.get(serverId);
        PalworldPaths paths = PalworldPaths.from(server);
        PathSecurityUtil.requireInsideRoot(server, paths.defaultSettingsFile());
        Path target = PathSecurityUtil.requireInsideRoot(server, paths.settingsFile());
        if (!Files.exists(paths.defaultSettingsFile())) {
            throw new IllegalArgumentException("No existe DefaultPalWorldSettings.ini.");
        }
        Files.createDirectories(target.getParent());
        Files.copy(paths.defaultSettingsFile(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void saveForm(Long serverId, Map<String, String> submitted) throws IOException {
        PalworldServer server = servers.get(serverId);
        PalworldPaths paths = PalworldPaths.from(server);
        Path target = PathSecurityUtil.requireInsideRoot(server, paths.settingsFile());
        String original = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
        backupIfExists(target);
        Map<String, String> parsed = IniParser.parseOptionSettings(original);
        Map<String, String> values = new LinkedHashMap<>();
        parsed.keySet().forEach(field -> {
            if (submitted.containsKey(field)) {
                values.put(field, normalizeSubmittedValue(field, submitted.get(field)));
            }
        });
        COMMON_FIELDS.stream()
                .filter(field -> !parsed.containsKey(field))
                .forEach(field -> {
                    String value = normalizeSubmittedValue(field, submitted.get(field));
                    if (value != null && !value.isBlank()) {
                        values.put(field, value);
                    }
                });
        Files.createDirectories(target.getParent());
        Files.writeString(target, IniParser.updateOptionSettings(original, values), StandardCharsets.UTF_8);
    }

    private List<ConfigField> formFields(Map<String, String> parsed) {
        Map<String, String> fields = new LinkedHashMap<>(parsed);
        COMMON_FIELDS.forEach(field -> fields.putIfAbsent(field, ""));
        return fields.entrySet().stream()
                .map(entry -> new ConfigField(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void backupIfExists(Path target) throws IOException {
        if (Files.exists(target)) {
            Path backup = target.resolveSibling(target.getFileName() + ".bak-" + BACKUP_FORMAT.format(LocalDateTime.now()));
            Files.copy(target, backup);
        }
    }

    private String normalizeSubmittedValue(String field, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (isQuotedSetting(field)) {
            if (trimmed.isBlank()) {
                return "\"\"";
            }
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return trimmed;
            }
            return "\"" + trimmed.replace("\"", "\\\"") + "\"";
        }
        return trimmed;
    }

    public void saveAdvanced(Long serverId, String content) throws IOException {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("El archivo de configuraciÃ³n no puede quedar vacÃ­o.");
        }
        PalworldServer server = servers.get(serverId);
        PalworldPaths paths = PalworldPaths.from(server);
        Path target = PathSecurityUtil.requireInsideRoot(server, paths.settingsFile());
        backupIfExists(target);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private boolean isQuotedSetting(String field) {
        return field.endsWith("Name")
                || field.endsWith("Description")
                || field.endsWith("Password")
                || field.equals("PublicIP")
                || field.equals("DeathPenalty")
                || field.equals("RandomizerSeed")
                || field.equals("Region")
                || field.equals("BanListURL")
                || field.equals("AdditionalDropItemWhenPlayerKillingInPvPMode");
    }

    public record ConfigView(
            PalworldServer server,
            PalworldPaths paths,
            String rawContent,
            List<ConfigField> fields,
            boolean settingsExists,
            boolean defaultSettingsExists,
            String serverStatus
    ) {
    }
}
