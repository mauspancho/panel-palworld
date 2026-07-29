package com.palworldadmin.app.service.rcon;

import com.palworldadmin.app.dto.RconPlayerView;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.RconWelcomeMessageConfig;
import com.palworldadmin.app.repository.PalworldServerRepository;
import com.palworldadmin.app.repository.RconWelcomeMessageConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RconWelcomeMessageService {
    private static final int DEFAULT_DELAY_SECONDS = 20;
    private static final int MAX_DELAY_SECONDS = 3600;
    private static final int MAX_MESSAGES = 20;

    private final PalworldServerRepository servers;
    private final RconWelcomeMessageConfigRepository configs;

    public RconWelcomeMessageService(PalworldServerRepository servers, RconWelcomeMessageConfigRepository configs) {
        this.servers = servers;
        this.configs = configs;
    }

    @Transactional(readOnly = true)
    public WelcomeConfigView view(Long serverId) {
        PalworldServer server = server(serverId);
        RconWelcomeMessageConfig config = configs.findByServer(server).orElseGet(() -> defaultConfig(server));
        return view(config);
    }

    @Transactional
    public WelcomeConfigView save(Long serverId, boolean enabled, Integer delaySeconds, List<String> messages) {
        PalworldServer server = server(serverId);
        RconWelcomeMessageConfig config = configs.findByServer(server).orElseGet(() -> defaultConfig(server));
        config.setEnabled(enabled);
        config.setDelaySeconds(normalizeDelay(delaySeconds));
        config.setMessages(normalizeMessages(messages));
        if (config.getNextMessageIndex() >= config.getMessages().size()) {
            config.setNextMessageIndex(0);
        }
        return view(configs.save(config));
    }

    @Transactional(readOnly = true)
    public Optional<Integer> delaySeconds(Long serverId) {
        PalworldServer server = server(serverId);
        return configs.findByServer(server)
                .filter(RconWelcomeMessageConfig::isEnabled)
                .filter(config -> !config.getMessages().isEmpty())
                .map(RconWelcomeMessageConfig::getDelaySeconds);
    }

    @Transactional
    public Optional<String> nextMessage(Long serverId, RconPlayerView player) {
        PalworldServer server = server(serverId);
        Optional<RconWelcomeMessageConfig> current = configs.findByServer(server)
                .filter(RconWelcomeMessageConfig::isEnabled)
                .filter(config -> !config.getMessages().isEmpty());
        if (current.isEmpty()) {
            return Optional.empty();
        }
        RconWelcomeMessageConfig config = current.get();
        int index = Math.floorMod(config.getNextMessageIndex(), config.getMessages().size());
        String template = config.getMessages().get(index);
        config.setNextMessageIndex((index + 1) % config.getMessages().size());
        configs.save(config);
        return Optional.of(render(template, server, player));
    }

    @Transactional
    public void deleteForServer(PalworldServer server) {
        configs.deleteByServer(server);
    }

    private PalworldServer server(Long serverId) {
        return servers.findById(serverId).orElseThrow(() -> new IllegalArgumentException("Servidor no encontrado."));
    }

    private RconWelcomeMessageConfig defaultConfig(PalworldServer server) {
        RconWelcomeMessageConfig config = new RconWelcomeMessageConfig();
        config.setServer(server);
        config.setEnabled(false);
        config.setDelaySeconds(DEFAULT_DELAY_SECONDS);
        config.setMessages(List.of("Bienvenido {player} a {server}."));
        return config;
    }

    private WelcomeConfigView view(RconWelcomeMessageConfig config) {
        return new WelcomeConfigView(
                config.getServer().getId(),
                config.getServer().getName(),
                config.isEnabled(),
                config.getDelaySeconds(),
                List.copyOf(config.getMessages())
        );
    }

    private Integer normalizeDelay(Integer delaySeconds) {
        if (delaySeconds == null) {
            return DEFAULT_DELAY_SECONDS;
        }
        return Math.max(0, Math.min(delaySeconds, MAX_DELAY_SECONDS));
    }

    private List<String> normalizeMessages(List<String> messages) {
        if (messages == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String message : messages) {
            if (message == null || message.isBlank()) {
                continue;
            }
            String trimmed = message.replace("\r", " ").replace("\n", " ").trim();
            if (trimmed.length() > 300) {
                throw new IllegalArgumentException("Cada mensaje RCON debe tener maximo 300 caracteres.");
            }
            normalized.add(trimmed);
            if (normalized.size() >= MAX_MESSAGES) {
                break;
            }
        }
        return normalized;
    }

    private String render(String template, PalworldServer server, RconPlayerView player) {
        return template
                .replace("{player}", emptyFallback(player.name(), "Jugador"))
                .replace("{playerId}", emptyFallback(player.playerId(), "sin-id"))
                .replace("{platform}", emptyFallback(player.platformId(), "sin-plataforma"))
                .replace("{server}", server.getName());
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record WelcomeConfigView(Long serverId, String serverName, boolean enabled, int delaySeconds, List<String> messages) {
    }
}
