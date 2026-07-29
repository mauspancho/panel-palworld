package com.palworldadmin.app.service.configprofile;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigProfileServiceTest {
    private static final Long SERVER_ID = 1L;

    @TempDir
    Path tempDir;

    private PalworldServer server;
    private PalworldServerService servers;
    private ActionLogService actionLogs;
    private ConfigProfileService service;
    private Path settingsFile;

    @BeforeEach
    void setUp() throws Exception {
        Path root = tempDir.resolve("palworld");
        settingsFile = root.resolve("Pal/Saved/Config/LinuxServer/PalWorldSettings.ini");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, settings("Base", "secret-admin", "secret-server", "1.000000"), StandardCharsets.UTF_8);

        server = new PalworldServer();
        server.setId(SERVER_ID);
        server.setName("palworld");
        server.setRootPath(root.toString());

        servers = mock(PalworldServerService.class);
        actionLogs = mock(ActionLogService.class);
        when(servers.get(SERVER_ID)).thenReturn(server);

        service = new ConfigProfileService(
                servers,
                actionLogs,
                tempDir.resolve("profiles").toString(),
                tempDir.resolve("backups").toString()
        );
    }

    @Test
    void createsDefaultProfileOnceWithoutOverwritingIt() throws Exception {
        var first = service.list(SERVER_ID);
        Files.writeString(settingsFile, settings("Changed", "other-admin", "other-server", "2.000000"), StandardCharsets.UTF_8);
        var second = service.list(SERVER_ID);
        var detail = service.get(SERVER_ID, "default");

        assertThat(first.profiles()).hasSize(1);
        assertThat(second.profiles()).hasSize(1);
        assertThat(detail.name()).isEqualTo("default");
        assertThat(detail.configuration()).contains("ServerName=\"Base\"");
        assertThat(detail.configuration()).doesNotContain("secret-admin").doesNotContain("secret-server");
        assertThat(second.externalModified()).isTrue();
    }

    @Test
    void createsDuplicatesAndProtectsDefaultAndActiveProfiles() {
        service.list(SERVER_ID);
        var event = service.createFromActive(SERVER_ID, new ConfigProfileService.ProfileWriteRequest("Evento XP", "Fin de semana"), "admin");
        var copy = service.duplicate(SERVER_ID, event.id(), new ConfigProfileService.DuplicateRequest("Evento XP copia", "duplicado"), "admin");

        assertThat(service.list(SERVER_ID).profiles()).extracting(ConfigProfileService.ProfileSummaryView::id)
                .contains("default", event.id(), copy.id());
        assertThatThrownBy(() -> service.delete(SERVER_ID, "default", "admin"))
                .hasMessageContaining("default no puede eliminarse");
        assertThatThrownBy(() -> service.delete(SERVER_ID, "default", "admin"))
                .hasMessageContaining("default no puede eliminarse");
    }

    @Test
    void appliesProfileAtomicallyWithBackupAndPreservesActiveSecrets() throws Exception {
        service.list(SERVER_ID);
        var event = service.createFromActive(SERVER_ID, new ConfigProfileService.ProfileWriteRequest("Evento XP", ""), "admin");
        service.update(
                SERVER_ID,
                event.id(),
                new ConfigProfileService.ProfileUpdateRequest("Evento XP", "", settings("Evento", "should-not-apply", "should-not-apply", "5.000000")),
                "admin"
        );

        var result = service.apply(SERVER_ID, event.id(), "admin");
        String active = Files.readString(settingsFile, StandardCharsets.UTF_8);

        assertThat(result.success()).isTrue();
        assertThat(result.changes()).extracting(ConfigProfileService.DiffEntryView::key).contains("ServerName", "ExpRate");
        assertThat(active).contains("ServerName=\"Evento\"");
        assertThat(active).contains("ExpRate=5.000000");
        assertThat(active).contains("AdminPassword=\"secret-admin\"");
        assertThat(active).contains("ServerPassword=\"secret-server\"");
        assertThat(Files.exists(Path.of(result.backupPath()))).isTrue();
        verify(actionLogs).record(any(), anyString(), anyString(), anyString(), anyString(), isNull(), anyBoolean());
    }

    @Test
    void importsAndExportsProfileWithoutSecrets() {
        service.list(SERVER_ID);
        String importedJson = """
                {
                  "schemaVersion": 1,
                  "name": "Importado",
                  "description": "Perfil externo",
                  "configuration": "%s"
                }
                """.formatted(escapeJson(settings("Importado", "raw-secret", "raw-server", "3.000000")));

        var imported = service.importProfile(SERVER_ID, importedJson, "admin");
        var exported = service.exportProfile(SERVER_ID, imported.id(), "admin");

        assertThat(imported.name()).isEqualTo("Importado");
        assertThat(exported.configuration).contains("ServerName=\"Importado\"");
        assertThat(exported.configuration).doesNotContain("raw-secret").doesNotContain("raw-server");
        assertThat(exported.configuration).contains("__PALWORLD_ADMIN_SECRET__");
    }

    private String settings(String serverName, String adminPassword, String serverPassword, String expRate) {
        return "[/Script/Pal.PalGameWorldSettings]\n"
                + "OptionSettings=(ServerName=\"" + serverName + "\",AdminPassword=\"" + adminPassword + "\",ServerPassword=\"" + serverPassword + "\",ExpRate=" + expRate + ",PalCaptureRate=1.000000)\n";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
