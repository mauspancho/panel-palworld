package com.palworldadmin.app.service.configprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.server.PalworldPaths;
import com.palworldadmin.app.util.IniParser;
import com.palworldadmin.app.util.PathSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

@Service
public class ConfigProfileService {
    private static final int SCHEMA_VERSION = 1;
    private static final String DEFAULT_ID = "default";
    private static final String DEFAULT_NAME = "default";
    private static final int MAX_PROFILE_BYTES = 512 * 1024;
    private static final int MAX_BACKUPS_PER_SERVER = 30;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,79}");
    private static final List<String> SECRET_KEYS = List.of("AdminPassword", "ServerPassword");

    private final PalworldServerService servers;
    private final ActionLogService actionLogs;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final Path backupRoot;
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ConfigProfileService(
            PalworldServerService servers,
            ActionLogService actionLogs,
            @Value("${palworld-admin.config-profiles-root:data/config-profiles}") String storageRoot,
            @Value("${palworld-admin.config-profile-backups-root:data/config-profile-backups}") String backupRoot
    ) {
        this.servers = servers;
        this.actionLogs = actionLogs;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.backupRoot = Path.of(backupRoot).toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ProfileListView list(Long serverId) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            String activeHash = activeSanitizedHash(server);
            boolean externalModified = index.activeProfileId != null
                    && index.profiles.stream()
                    .filter(profile -> index.activeProfileId.equals(profile.id))
                    .findFirst()
                    .map(profile -> !Objects.equals(profile.hash, activeHash))
                    .orElse(false);
            List<ProfileSummaryView> items = index.profiles.stream()
                    .sorted(Comparator.comparing((ProfileMetadata profile) -> !profile.isDefault).thenComparing(profile -> profile.name.toLowerCase(Locale.ROOT)))
                    .map(profile -> summary(profile, Objects.equals(profile.id, index.activeProfileId)))
                    .toList();
            return new ProfileListView(server.getId(), server.getName(), index.activeProfileId, index.defaultProfileId, externalModified, items);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron leer los perfiles de configuracion: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ProfileDetailView get(Long serverId, String profileId) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileMetadata metadata = requireProfile(index, profileId);
            ProfileDocument document = readProfile(server, metadata);
            return detail(document, Objects.equals(index.activeProfileId, document.id));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ProfileDetailView createFromActive(Long serverId, ProfileWriteRequest request, String username) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            String now = now();
            String content = readActiveConfig(server);
            String sanitized = sanitizeSecrets(content);
            String id = uniqueId(index, slug(request.name()));
            ProfileDocument document = new ProfileDocument(
                    SCHEMA_VERSION,
                    id,
                    requiredName(request.name()),
                    cleanDescription(request.description()),
                    false,
                    now,
                    now,
                    username,
                    username,
                    sanitized,
                    normalizedHash(sanitized),
                    parameterCount(sanitized)
            );
            validateProfile(document);
            writeProfile(server, document);
            index.profiles.add(metadata(document));
            writeIndex(server, index);
            actionLogs.record(server, "config-profile-create", username, "Perfil creado: " + document.name, null, null, true);
            return detail(document, false);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ProfileDetailView update(Long serverId, String profileId, ProfileUpdateRequest request, String username) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileMetadata existing = requireProfile(index, profileId);
            ProfileDocument current = readProfile(server, existing);
            String updatedConfiguration = request.configuration() == null
                    ? current.configuration
                    : sanitizeSecrets(request.configuration());
            ProfileDocument updated = new ProfileDocument(
                    SCHEMA_VERSION,
                    current.id,
                    requiredName(request.name() == null ? current.name : request.name()),
                    cleanDescription(request.description() == null ? current.description : request.description()),
                    current.isDefault,
                    current.createdAt,
                    now(),
                    current.createdBy,
                    username,
                    updatedConfiguration,
                    normalizedHash(updatedConfiguration),
                    parameterCount(updatedConfiguration)
            );
            validateProfile(updated);
            writeProfile(server, updated);
            replaceMetadata(index, metadata(updated));
            writeIndex(server, index);
            actionLogs.record(server, "config-profile-edit", username, "Perfil editado: " + updated.name, null, null, true);
            return detail(updated, Objects.equals(index.activeProfileId, updated.id));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo actualizar el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ProfileDetailView duplicate(Long serverId, String profileId, DuplicateRequest request, String username) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileDocument source = readProfile(server, requireProfile(index, profileId));
            String now = now();
            String id = uniqueId(index, slug(request.name()));
            ProfileDocument copy = new ProfileDocument(
                    SCHEMA_VERSION,
                    id,
                    requiredName(request.name()),
                    cleanDescription(request.description() == null ? source.description : request.description()),
                    false,
                    now,
                    now,
                    username,
                    username,
                    source.configuration,
                    normalizedHash(source.configuration),
                    parameterCount(source.configuration)
            );
            validateProfile(copy);
            writeProfile(server, copy);
            index.profiles.add(metadata(copy));
            writeIndex(server, index);
            actionLogs.record(server, "config-profile-duplicate", username, "Perfil duplicado desde " + source.name + ": " + copy.name, null, null, true);
            return detail(copy, false);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo duplicar el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ApplyResultView apply(Long serverId, String profileId, String username) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileDocument target = readProfile(server, requireProfile(index, profileId));
            validateProfile(target);
            String previous = readActiveConfig(server);
            String previousProfileId = index.activeProfileId;
            Path backup = writeBackup(server, previous, previousProfileId, target.id, username, "pending");
            String configurationToApply = restoreActiveSecrets(target.configuration, previous);
            writeActiveConfig(server, configurationToApply);
            index.activeProfileId = target.id;
            replaceMetadata(index, metadata(target));
            writeIndex(server, index);
            writeBackup(server, previous, previousProfileId, target.id, username, "success");
            pruneBackups(server);
            List<DiffEntryView> changes = diff(previous, target.configuration);
            actionLogs.record(server, "config-profile-apply", username, "Perfil aplicado: " + target.name + ". Reinicia Palworld para tomar todos los cambios.", diffSummary(changes), null, true);
            return new ApplyResultView(true, "Perfil aplicado. Reinicia Palworld para tomar todos los cambios.", target.id, backup.toString(), changes);
        } catch (IOException | RuntimeException e) {
            PalworldServer server = servers.get(serverId);
            actionLogs.record(server, "config-profile-apply", username, "No se pudo aplicar el perfil.", null, e.getMessage(), false);
            throw new IllegalStateException("No se pudo aplicar el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ApplyResultView restoreDefault(Long serverId, String username) {
        return apply(serverId, DEFAULT_ID, username);
    }

    public void delete(Long serverId, String profileId, String username) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileMetadata metadata = requireProfile(index, profileId);
            if (metadata.isDefault || DEFAULT_ID.equals(metadata.id)) {
                throw new IllegalArgumentException("El perfil default no puede eliminarse.");
            }
            if (Objects.equals(index.activeProfileId, metadata.id)) {
                throw new IllegalArgumentException("No puedes eliminar el perfil activo. Aplica otro perfil primero.");
            }
            Path path = profilePath(server, metadata.id);
            index.profiles.removeIf(profile -> Objects.equals(profile.id, metadata.id));
            writeIndex(server, index);
            Files.deleteIfExists(path);
            actionLogs.record(server, "config-profile-delete", username, "Perfil eliminado: " + metadata.name, null, null, true);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo eliminar el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ProfileExportView exportProfile(Long serverId, String profileId, String username) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileDocument document = readProfile(server, requireProfile(index, profileId));
            ProfileExportView exported = new ProfileExportView(SCHEMA_VERSION, document.name, document.description, document.configuration);
            actionLogs.record(server, "config-profile-export", username, "Perfil exportado: " + document.name, null, null, true);
            return exported;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo exportar el perfil: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public ProfileDetailView importProfile(Long serverId, String rawJson, String username) {
        if (rawJson == null || rawJson.isBlank() || rawJson.getBytes(StandardCharsets.UTF_8).length > MAX_PROFILE_BYTES) {
            throw new IllegalArgumentException("El archivo de importacion es invalido o demasiado grande.");
        }
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileExportView imported = objectMapper.readValue(rawJson, ProfileExportView.class);
            if (imported.schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Version de esquema no soportada.");
            }
            String now = now();
            String sanitized = sanitizeSecrets(imported.configuration);
            String id = uniqueId(index, slug(imported.name));
            ProfileDocument document = new ProfileDocument(
                    SCHEMA_VERSION,
                    id,
                    requiredName(imported.name),
                    cleanDescription(imported.description),
                    false,
                    now,
                    now,
                    username,
                    username,
                    sanitized,
                    normalizedHash(sanitized),
                    parameterCount(sanitized)
            );
            validateProfile(document);
            writeProfile(server, document);
            index.profiles.add(metadata(document));
            writeIndex(server, index);
            actionLogs.record(server, "config-profile-import", username, "Perfil importado: " + document.name, null, null, true);
            return detail(document, false);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo importar el perfil JSON.", e);
        } finally {
            lock.unlock();
        }
    }

    public List<DiffEntryView> diff(Long serverId, String profileId) {
        ReentrantLock lock = lock(serverId);
        lock.lock();
        try {
            PalworldServer server = servers.get(serverId);
            ProfileIndex index = ensureDefaultProfile(server);
            ProfileDocument target = readProfile(server, requireProfile(index, profileId));
            return diff(readActiveConfig(server), target.configuration);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo calcular el resumen de cambios: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private ProfileIndex ensureDefaultProfile(PalworldServer server) throws IOException {
        Files.createDirectories(profilesDir(server));
        ProfileIndex index = readIndex(server);
        if (index.defaultProfileId == null || index.defaultProfileId.isBlank()) {
            index.defaultProfileId = DEFAULT_ID;
        }
        boolean hasDefault = index.profiles.stream().anyMatch(profile -> DEFAULT_ID.equals(profile.id));
        if (!hasDefault) {
            String now = now();
            String active = sanitizeSecrets(readActiveConfig(server));
            ProfileDocument document = new ProfileDocument(
                    SCHEMA_VERSION,
                    DEFAULT_ID,
                    DEFAULT_NAME,
                    "Configuracion base capturada automaticamente desde el archivo activo.",
                    true,
                    now,
                    now,
                    "system",
                    "system",
                    active,
                    normalizedHash(active),
                    parameterCount(active)
            );
            validateProfile(document);
            writeProfile(server, document);
            index.profiles.add(metadata(document));
            index.defaultProfileId = DEFAULT_ID;
            if (index.activeProfileId == null || index.activeProfileId.isBlank()) {
                index.activeProfileId = DEFAULT_ID;
            }
            writeIndex(server, index);
            actionLogs.record(server, "config-profile-migrate-default", "system", "Perfil default creado desde la configuracion activa.", null, null, true);
        }
        long defaultCount = index.profiles.stream().filter(profile -> profile.isDefault).count();
        if (defaultCount != 1 || index.profiles.stream().noneMatch(profile -> DEFAULT_ID.equals(profile.id) && profile.isDefault)) {
            index.profiles.forEach(profile -> profile.isDefault = DEFAULT_ID.equals(profile.id));
            index.defaultProfileId = DEFAULT_ID;
            writeIndex(server, index);
        }
        return index;
    }

    private ProfileIndex readIndex(PalworldServer server) throws IOException {
        Path path = indexPath(server);
        if (!Files.exists(path)) {
            return new ProfileIndex(DEFAULT_ID, null, new ArrayList<>());
        }
        ProfileIndex index = objectMapper.readValue(path.toFile(), ProfileIndex.class);
        if (index.profiles == null) {
            index.profiles = new ArrayList<>();
        }
        return index;
    }

    private void writeIndex(PalworldServer server, ProfileIndex index) throws IOException {
        writeJsonAtomic(indexPath(server), index);
    }

    private ProfileDocument readProfile(PalworldServer server, ProfileMetadata metadata) throws IOException {
        Path path = profilePath(server, metadata.id);
        ProfileDocument document = objectMapper.readValue(path.toFile(), ProfileDocument.class);
        validateProfile(document);
        return document;
    }

    private void writeProfile(PalworldServer server, ProfileDocument document) throws IOException {
        writeJsonAtomic(profilePath(server, document.id), document);
    }

    private String readActiveConfig(PalworldServer server) throws IOException {
        PalworldPaths paths = PalworldPaths.from(server);
        Path target = PathSecurityUtil.requireInsideRoot(server, paths.settingsFile());
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("No existe PalWorldSettings.ini para capturar o comparar configuracion.");
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    private void writeActiveConfig(PalworldServer server, String content) throws IOException {
        PalworldPaths paths = PalworldPaths.from(server);
        Path target = PathSecurityUtil.requireInsideRoot(server, paths.settingsFile());
        Files.createDirectories(target.getParent());
        writeStringAtomic(target, content);
    }

    private Path writeBackup(PalworldServer server, String previous, String fromProfileId, String toProfileId, String username, String result) throws IOException {
        Path dir = backupDir(server);
        Files.createDirectories(dir);
        BackupDocument backup = new BackupDocument(SCHEMA_VERSION, server.getId(), server.getName(), fromProfileId, toProfileId, now(), username, result, previous);
        Path target = dir.resolve(FILE_DATE.format(LocalDateTime.now()) + "-" + safeFilePart(toProfileId) + ".json");
        writeJsonAtomic(target, backup);
        return target;
    }

    private void pruneBackups(PalworldServer server) throws IOException {
        Path dir = backupDir(server);
        if (!Files.exists(dir)) {
            return;
        }
        List<Path> backups;
        try (var stream = Files.list(dir)) {
            backups = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
        }
        for (int i = MAX_BACKUPS_PER_SERVER; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }

    private String sanitizeSecrets(String content) {
        Map<String, String> updates = new LinkedHashMap<>();
        Map<String, String> values = IniParser.parseOptionSettings(content);
        SECRET_KEYS.stream()
                .filter(values::containsKey)
                .forEach(key -> updates.put(key, "\"__PALWORLD_ADMIN_SECRET__\""));
        return updates.isEmpty() ? content : IniParser.updateOptionSettings(content, updates);
    }

    private String restoreActiveSecrets(String profileContent, String activeContent) {
        Map<String, String> activeValues = IniParser.parseOptionSettings(activeContent);
        Map<String, String> profileValues = IniParser.parseOptionSettings(profileContent);
        Map<String, String> updates = new LinkedHashMap<>();
        SECRET_KEYS.stream()
                .filter(activeValues::containsKey)
                .filter(profileValues::containsKey)
                .forEach(key -> updates.put(key, activeValues.get(key)));
        return updates.isEmpty() ? profileContent : IniParser.updateOptionSettings(profileContent, updates);
    }

    private String activeSanitizedHash(PalworldServer server) throws IOException {
        return normalizedHash(sanitizeSecrets(readActiveConfig(server)));
    }

    private String normalizedHash(String content) {
        return sha256(normalizedContent(content));
    }

    private String normalizedContent(String content) {
        Map<String, String> values = IniParser.parseOptionSettings(content);
        if (values.isEmpty()) {
            return content == null ? "" : content.replaceAll("\\s+", " ").trim();
        }
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().trim() + "=" + entry.getValue().trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }

    private List<DiffEntryView> diff(String activeContent, String profileContent) {
        Map<String, String> active = IniParser.parseOptionSettings(sanitizeSecrets(activeContent));
        Map<String, String> target = IniParser.parseOptionSettings(sanitizeSecrets(profileContent));
        Map<String, String> merged = new HashMap<>();
        active.forEach(merged::put);
        target.forEach(merged::put);
        return merged.keySet().stream()
                .sorted()
                .filter(key -> !Objects.equals(active.get(key), target.get(key)))
                .map(key -> new DiffEntryView(key, active.get(key), target.get(key)))
                .toList();
    }

    private String diffSummary(List<DiffEntryView> changes) {
        if (changes.isEmpty()) {
            return "Sin diferencias.";
        }
        return changes.stream()
                .limit(50)
                .map(change -> change.key() + ": " + valueOrDash(change.previousValue()) + " -> " + valueOrDash(change.newValue()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private String valueOrDash(String value) {
        return value == null ? "-" : value;
    }

    private void validateProfile(ProfileDocument document) {
        if (document.schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Version de perfil no soportada.");
        }
        if (!SAFE_ID.matcher(document.id).matches()) {
            throw new IllegalArgumentException("ID de perfil invalido.");
        }
        requiredName(document.name);
        if (document.configuration == null || document.configuration.isBlank()) {
            throw new IllegalArgumentException("La configuracion del perfil no puede estar vacia.");
        }
        if (document.configuration.getBytes(StandardCharsets.UTF_8).length > MAX_PROFILE_BYTES) {
            throw new IllegalArgumentException("La configuracion del perfil excede el tamano permitido.");
        }
        if (IniParser.parseOptionSettings(document.configuration).isEmpty()) {
            throw new IllegalArgumentException("El perfil no contiene OptionSettings valido.");
        }
    }

    private int parameterCount(String content) {
        return IniParser.parseOptionSettings(content).size();
    }

    private ProfileMetadata requireProfile(ProfileIndex index, String profileId) {
        String safeId = normalizeProfileId(profileId);
        return index.profiles.stream()
                .filter(profile -> Objects.equals(profile.id, safeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado."));
    }

    private void replaceMetadata(ProfileIndex index, ProfileMetadata replacement) {
        for (int i = 0; i < index.profiles.size(); i++) {
            if (Objects.equals(index.profiles.get(i).id, replacement.id)) {
                index.profiles.set(i, replacement);
                return;
            }
        }
        index.profiles.add(replacement);
    }

    private ProfileMetadata metadata(ProfileDocument document) {
        return new ProfileMetadata(
                document.id,
                document.name,
                document.description,
                document.isDefault,
                document.createdAt,
                document.updatedAt,
                document.createdBy,
                document.updatedBy,
                profileFileName(document.id),
                document.hash,
                document.parameterCount
        );
    }

    private ProfileSummaryView summary(ProfileMetadata metadata, boolean active) {
        return new ProfileSummaryView(metadata.id, metadata.name, metadata.description, metadata.isDefault, active, metadata.createdAt, metadata.updatedAt, metadata.createdBy, metadata.updatedBy, metadata.hash, metadata.parameterCount);
    }

    private ProfileDetailView detail(ProfileDocument document, boolean active) {
        return new ProfileDetailView(document.id, document.name, document.description, document.isDefault, active, document.createdAt, document.updatedAt, document.createdBy, document.updatedBy, document.hash, document.parameterCount, document.configuration);
    }

    private String uniqueId(ProfileIndex index, String base) {
        String candidate = base == null || base.isBlank() ? "perfil" : base;
        int suffix = 2;
        while (containsProfile(index, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean containsProfile(ProfileIndex index, String id) {
        return index.profiles.stream().anyMatch(profile -> Objects.equals(profile.id, id));
    }

    private String slug(String value) {
        String base = value == null ? "perfil" : value.trim().toLowerCase(Locale.ROOT);
        base = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        base = base.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "perfil";
        }
        if (base.length() > 64) {
            base = base.substring(0, 64).replaceAll("-+$", "");
        }
        return normalizeProfileId(base);
    }

    private String normalizeProfileId(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("ID de perfil invalido.");
        }
        return id;
    }

    private String requiredName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del perfil es obligatorio.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("El nombre del perfil es demasiado largo.");
        }
        return trimmed;
    }

    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }
        String trimmed = description.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private String safeFilePart(String value) {
        return value == null ? "perfil" : value.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    private String profileFileName(String profileId) {
        return normalizeProfileId(profileId) + ".json";
    }

    private Path serverDir(PalworldServer server) {
        return storageRoot.resolve("server-" + server.getId()).normalize();
    }

    private Path profilesDir(PalworldServer server) {
        return serverDir(server).resolve("profiles").normalize();
    }

    private Path indexPath(PalworldServer server) {
        return serverDir(server).resolve("config-profiles.json").normalize();
    }

    private Path profilePath(PalworldServer server, String profileId) {
        return profilesDir(server).resolve(profileFileName(profileId)).normalize();
    }

    private Path backupDir(PalworldServer server) {
        return backupRoot.resolve("server-" + server.getId()).normalize();
    }

    private ReentrantLock lock(Long serverId) {
        return locks.computeIfAbsent(serverId, ignored -> new ReentrantLock());
    }

    private void writeJsonAtomic(Path target, Object value) throws IOException {
        Files.createDirectories(target.getParent());
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        writeBytesAtomic(target, bytes);
    }

    private void writeStringAtomic(Path target, String value) throws IOException {
        writeBytesAtomic(target, value.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytesAtomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String now() {
        return LocalDateTime.now().toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfileIndex {
        public String defaultProfileId;
        public String activeProfileId;
        public List<ProfileMetadata> profiles = new ArrayList<>();

        public ProfileIndex() {
        }

        public ProfileIndex(String defaultProfileId, String activeProfileId, List<ProfileMetadata> profiles) {
            this.defaultProfileId = defaultProfileId;
            this.activeProfileId = activeProfileId;
            this.profiles = profiles;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfileMetadata {
        public String id;
        public String name;
        public String description;
        public boolean isDefault;
        public String createdAt;
        public String updatedAt;
        public String createdBy;
        public String updatedBy;
        public String fileName;
        public String hash;
        public int parameterCount;

        public ProfileMetadata() {
        }

        public ProfileMetadata(String id, String name, String description, boolean isDefault, String createdAt, String updatedAt, String createdBy, String updatedBy, String fileName, String hash, int parameterCount) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.isDefault = isDefault;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
            this.fileName = fileName;
            this.hash = hash;
            this.parameterCount = parameterCount;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfileDocument {
        public int schemaVersion;
        public String id;
        public String name;
        public String description;
        public boolean isDefault;
        public String createdAt;
        public String updatedAt;
        public String createdBy;
        public String updatedBy;
        public String configuration;
        public String hash;
        public int parameterCount;

        public ProfileDocument() {
        }

        public ProfileDocument(int schemaVersion, String id, String name, String description, boolean isDefault, String createdAt, String updatedAt, String createdBy, String updatedBy, String configuration, String hash, int parameterCount) {
            this.schemaVersion = schemaVersion;
            this.id = id;
            this.name = name;
            this.description = description;
            this.isDefault = isDefault;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
            this.configuration = configuration;
            this.hash = hash;
            this.parameterCount = parameterCount;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BackupDocument {
        public int schemaVersion;
        public Long serverId;
        public String serverName;
        public String fromProfileId;
        public String toProfileId;
        public String createdAt;
        public String username;
        public String result;
        public String previousConfiguration;

        public BackupDocument() {
        }

        public BackupDocument(int schemaVersion, Long serverId, String serverName, String fromProfileId, String toProfileId, String createdAt, String username, String result, String previousConfiguration) {
            this.schemaVersion = schemaVersion;
            this.serverId = serverId;
            this.serverName = serverName;
            this.fromProfileId = fromProfileId;
            this.toProfileId = toProfileId;
            this.createdAt = createdAt;
            this.username = username;
            this.result = result;
            this.previousConfiguration = previousConfiguration;
        }
    }

    public record ProfileListView(Long serverId, String serverName, String activeProfileId, String defaultProfileId, boolean externalModified, List<ProfileSummaryView> profiles) {
    }

    public record ProfileSummaryView(String id, String name, String description, boolean isDefault, boolean active, String createdAt, String updatedAt, String createdBy, String updatedBy, String hash, int parameterCount) {
    }

    public record ProfileDetailView(String id, String name, String description, boolean isDefault, boolean active, String createdAt, String updatedAt, String createdBy, String updatedBy, String hash, int parameterCount, String configuration) {
    }

    public record ProfileWriteRequest(String name, String description) {
    }

    public record ProfileUpdateRequest(String name, String description, String configuration) {
    }

    public record DuplicateRequest(String name, String description) {
    }

    public record ApplyResultView(boolean success, String message, String profileId, String backupPath, List<DiffEntryView> changes) {
    }

    public record DiffEntryView(String key, String previousValue, String newValue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfileExportView {
        public int schemaVersion;
        public String name;
        public String description;
        public String configuration;

        public ProfileExportView() {
        }

        public ProfileExportView(int schemaVersion, String name, String description, String configuration) {
            this.schemaVersion = schemaVersion;
            this.name = name;
            this.description = description;
            this.configuration = configuration;
        }
    }
}
