package com.palworldadmin.app.controller;

import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerType;
import com.palworldadmin.app.dto.RconPlayerView;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.repository.PlayerConnectionSessionRepository;
import com.palworldadmin.app.repository.PlayerOnlineSnapshotRepository;
import com.palworldadmin.app.repository.RegisteredPlayerRepository;
import com.palworldadmin.app.service.PlayerOnlineSnapshotService;
import com.palworldadmin.app.service.PlayerPresenceService;
import com.palworldadmin.app.service.worldstats.GuildMemberStats;
import com.palworldadmin.app.service.worldstats.GuildPlayerRef;
import com.palworldadmin.app.service.worldstats.GuildStats;
import com.palworldadmin.app.service.worldstats.WorldStatsResult;
import com.palworldadmin.app.service.worldstats.WorldStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ApiControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private PalworldServerRepository servers;

    @Autowired
    private PlayerOnlineSnapshotRepository snapshotRepository;

    @Autowired
    private RegisteredPlayerRepository registeredPlayers;

    @Autowired
    private PlayerConnectionSessionRepository playerSessions;

    @Autowired
    private PlayerOnlineSnapshotService playerSnapshots;

    @Autowired
    private PlayerPresenceService playerPresence;

    @MockBean
    private WorldStatsService worldStats;

    @BeforeEach
    void configureSaveStats() {
        when(worldStats.aggregate(anyList())).thenReturn(WorldStatsResult.unavailable("No configurado en pruebas."));
        when(worldStats.read(any(PalworldServer.class))).thenReturn(WorldStatsResult.unavailable("No configurado en pruebas."));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardApiReturnsServersWithoutRconPassword() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Modern Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-modern.service");
        server.setRootPath("C:/palworld-modern");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        server.setPublicPort(8211);
        server.setRconEnabled(true);
        server.setRconHost("127.0.0.1");
        server.setRconPort(25575);
        server.setRconPassword("secret-rcon-password");
        servers.save(server);

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Modern Server")))
                .andExpect(content().string(containsString("25575")))
                .andExpect(content().string(not(containsString("secret-rcon-password"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardApiIncludesPersistentWorldStats() throws Exception {
        when(worldStats.aggregate(anyList())).thenReturn(WorldStatsResult.available(100, 20, 5, List.of()));
        PalworldServer server = new PalworldServer();
        server.setName("World Stats Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-world-stats.service");
        server.setRootPath("C:/palworld-world-stats");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"worldStats\":{\"schemaVersion\":1")))
                .andExpect(content().string(containsString("\"totalPlayers\":100")))
                .andExpect(content().string(containsString("\"playersWithBase\":20")))
                .andExpect(content().string(containsString("\"totalGuilds\":5")))
                .andExpect(content().string(containsString("\"available\":true")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void editServerFormPersistsWorldStatsPath() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Editable Stats Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-editable-stats.service");
        server.setRootPath("/home/usuario/palworld");
        server.setSteamcmdPath("/usr/games/steamcmd");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        server.setPublicPort(8211);
        servers.save(server);

        mvc.perform(post("/servers")
                        .with(csrf())
                        .param("id", server.getId().toString())
                        .param("name", "Editable Stats Server")
                        .param("type", "SYSTEMD")
                        .param("serviceName", "palworld-editable-stats.service")
                        .param("rootPath", "/home/usuario/palworld")
                        .param("steamcmdPath", "/usr/games/steamcmd")
                        .param("linuxUser", "palworld")
                        .param("linuxGroup", "palworld")
                        .param("publicPort", "8211")
                        .param("worldStatsPath", "  /home/usuario/palworld/jugadores/world-stats.json  ")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());

        PalworldServer saved = servers.findById(server.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getWorldStatsPath())
                .isEqualTo("/home/usuario/palworld/jugadores/world-stats.json");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void modernServerEditPersistsWorldStatsPath() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Modern Editable Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-modern-editable.service");
        server.setRootPath("/home/usuario/palworld");
        server.setSteamcmdPath("/usr/games/steamcmd");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        server.setPublicPort(8211);
        servers.save(server);

        mvc.perform(put("/api/servers/" + server.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Modern Editable Server",
                                  "type": "SYSTEMD",
                                  "serviceName": "palworld-modern-editable.service",
                                  "rootPath": "/home/usuario/palworld",
                                  "steamcmdPath": "/usr/games/steamcmd",
                                  "linuxUser": "palworld",
                                  "linuxGroup": "palworld",
                                  "publicPort": 8211,
                                  "worldStatsPath": "  /home/usuario/palworld/jugadores/world-stats.json  ",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"worldStatsPath\":\"/home/usuario/palworld/jugadores/world-stats.json\"")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateServerFromModernApi() throws Exception {
        mvc.perform(post("/api/servers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Nuevo Servidor",
                                  "type": "SYSTEMD",
                                  "serviceName": "palworld-nuevo.service",
                                  "rootPath": "/opt/palworld-servers/nuevo",
                                  "steamcmdPath": "/usr/games/steamcmd",
                                  "linuxUser": "palworld",
                                  "linuxGroup": "palworld",
                                  "publicPort": 8212,
                                  "worldStatsPath": "/opt/palworld-servers/nuevo/jugadores/world-stats.json",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"name\":\"Nuevo Servidor\"")))
                .andExpect(content().string(containsString("\"serviceName\":\"palworld-nuevo.service\"")))
                .andExpect(content().string(containsString("\"worldStatsPath\":\"/opt/palworld-servers/nuevo/jugadores/world-stats.json\"")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void guildApiReturnsGuildsAndMembersFromWorldStats() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Guild Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-guilds.service");
        server.setRootPath("C:/palworld-guilds");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        when(worldStats.read(any(PalworldServer.class))).thenReturn(WorldStatsResult.available(
                10,
                4,
                1,
                List.of(new GuildStats(
                        "guild-1",
                        "Los Admins",
                        new GuildPlayerRef("leader-uid", "Admin"),
                        3,
                        2,
                        List.of(
                                new GuildMemberStats("leader-uid", "Admin", true),
                                new GuildMemberStats("player-uid", "Pancho", false)
                        )
                ))
        ));

        mvc.perform(get("/api/guilds"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"serverName\":\"Guild Server\"")))
                .andExpect(content().string(containsString("\"name\":\"Los Admins\"")))
                .andExpect(content().string(containsString("\"bases\":3")))
                .andExpect(content().string(containsString("\"name\":\"Pancho\"")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void meApiReturnsAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin")))
                .andExpect(content().string(containsString("ROLE_ADMIN")));
    }

    @Test
    void corsAllowsConfiguredLanOrigins() throws Exception {
        mvc.perform(options("/api/auth/csrf")
                        .header("Origin", "http://192.168.1.50:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.50:5173"));
    }

    @Test
    void corsAllowsCloudflareTunnelOrigins() throws Exception {
        mvc.perform(options("/api/auth/csrf")
                        .header("Origin", "https://panel-palworld.trycloudflare.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://panel-palworld.trycloudflare.com"));
    }

    @Test
    void corsAllowsCloudflareTunnelDomain() throws Exception {
        mvc.perform(options("/api/auth/csrf")
                        .header("Origin", "https://demo.trycloudflare.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://demo.trycloudflare.com"));
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void regularUserCannotInstallServers() throws Exception {
        mvc.perform(post("/api/servers/999/actions/install").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void regularUserCanRunBasicServerActions() throws Exception {
        mvc.perform(post("/api/servers/999/actions/update").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void regularUserCannotReadRconConfig() throws Exception {
        mvc.perform(get("/api/servers/999/rcon/config"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void regularUserCannotManageWelcomeMessages() throws Exception {
        mvc.perform(get("/api/servers/999/rcon/welcome"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanSaveWelcomeMessages() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Welcome Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-welcome.service");
        server.setRootPath("C:/palworld-welcome");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        mvc.perform(put("/api/servers/" + server.getId() + "/rcon/welcome")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"delaySeconds\":20,\"messages\":[\"Bienvenido {player}\",\"Hola {player} en {server}\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"enabled\":true")))
                .andExpect(content().string(containsString("\"delaySeconds\":20")))
                .andExpect(content().string(containsString("Bienvenido {player}")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void playerAverageReturnsSessionAverage() throws Exception {
        playerSessions.deleteAll();
        registeredPlayers.deleteAll();
        snapshotRepository.deleteAll();

        PalworldServer server = new PalworldServer();
        server.setName("Average Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-average.service");
        server.setRootPath("C:/palworld-average");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        playerPresence.recordPresence(server, List.of(
                new RconPlayerView("Uno", "1", "Steam", "Uno,1,Steam"),
                new RconPlayerView("Dos", "2", "Steam", "Dos,2,Steam")
        ), now.minusSeconds(30));

        mvc.perform(get("/api/player-average?range=day"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"peakPlayers\":2")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void playerSnapshotUpdatesExistingBucketForLiveGraph() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Live Graph Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-live-graph.service");
        server.setRootPath("C:/palworld-live-graph");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        LocalDateTime capturedAt = LocalDateTime.now().withSecond(0).withNano(0).minusMinutes(15);
        playerSnapshots.recordSnapshot(server, List.of(), capturedAt);
        playerSnapshots.recordSnapshot(server, List.of(new RconPlayerView("Alice", "alice-uid", "Steam", "Alice,alice-uid,Steam")), capturedAt);

        mvc.perform(get("/api/servers/" + server.getId() + "/player-analytics?range=day"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice-uid")))
                .andExpect(content().string(containsString("\"totalMinutes\":15")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardCurrentGraphPointUsesActivePresence() throws Exception {
        playerSessions.deleteAll();
        registeredPlayers.deleteAll();
        snapshotRepository.deleteAll();

        PalworldServer server = new PalworldServer();
        server.setName("Live Dashboard Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-live-dashboard.service");
        server.setRootPath("C:/palworld-live-dashboard");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        int minute = (now.getMinute() / 15) * 15;
        LocalDateTime currentBucket = now.withMinute(minute);
        playerSnapshots.recordSnapshot(server, List.of(), currentBucket);
        playerPresence.recordPresence(server, List.of(new RconPlayerView("Alice", "alice-uid", "Steam", "Alice,alice-uid,Steam")), now);

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"actions\":1,\"players\":[\"Live Dashboard Server: Alice\"]")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void playerRegistryTracksActiveAndInactivePlayers() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Registry Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-registry.service");
        server.setRootPath("C:/palworld-registry");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        playerPresence.recordPresence(server, List.of(new RconPlayerView("Alice", "alice-uid", "Steam", "Alice,alice-uid,Steam")), now.minusMinutes(30));
        playerPresence.recordPresence(server, List.of(), now.minusMinutes(5));

        mvc.perform(get("/api/servers/" + server.getId() + "/player-registry?range=day"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"totalPlayers\":1")))
                .andExpect(content().string(containsString("\"activePlayers\":0")))
                .andExpect(content().string(containsString("\"inactivePlayers\":1")))
                .andExpect(content().string(containsString("alice-uid")))
                .andExpect(content().string(containsString("\"active\":false")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void stalePlayersAreClosedWhenRconStopsUpdatingPresence() throws Exception {
        playerSessions.deleteAll();
        registeredPlayers.deleteAll();

        PalworldServer server = new PalworldServer();
        server.setName("Stale Registry Server");
        server.setType(ServerType.SYSTEMD);
        server.setServiceName("palworld-stale-registry.service");
        server.setRootPath("C:/palworld-stale-registry");
        server.setLinuxUser("palworld");
        server.setLinuxGroup("palworld");
        servers.save(server);

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        playerPresence.recordPresence(server, List.of(new RconPlayerView("Bob", "bob-uid", "Steam", "Bob,bob-uid,Steam")), now.minusMinutes(5));
        playerPresence.expireStalePlayers(java.time.Duration.ofSeconds(90), now);

        mvc.perform(get("/api/servers/" + server.getId() + "/player-registry?range=day"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"activePlayers\":0")))
                .andExpect(content().string(containsString("\"active\":false")))
                .andExpect(content().string(containsString("bob-uid")));
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void regularUserCanOpenServerLogsPageData() throws Exception {
        PalworldServer server = new PalworldServer();
        server.setName("Logs Server");
        server.setType(ServerType.DOCKER);
        server.setContainerName("palworld-logs-test");
        server.setComposeProjectName("palworld-logs-test");
        server.setRootPath("C:/palworld-logs-test");
        servers.save(server);

        mvc.perform(get("/api/servers/" + server.getId() + "/logs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Logs Server")));
    }
}
