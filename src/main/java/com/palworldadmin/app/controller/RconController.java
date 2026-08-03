package com.palworldadmin.app.controller;

import com.palworldadmin.app.dto.RconPlayersView;
import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.PlayerPresenceService;
import com.palworldadmin.app.service.rcon.RconService;
import com.palworldadmin.app.service.rcon.RconWelcomeMessageService;
import com.palworldadmin.app.util.CommandResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class RconController {
    private final PalworldServerService servers;
    private final RconService rcon;
    private final ActionLogService actionLogs;
    private final RconWelcomeMessageService welcomeMessages;
    private final PlayerPresenceService playerPresence;

    public RconController(PalworldServerService servers, RconService rcon, ActionLogService actionLogs, RconWelcomeMessageService welcomeMessages, PlayerPresenceService playerPresence) {
        this.servers = servers;
        this.rcon = rcon;
        this.actionLogs = actionLogs;
        this.welcomeMessages = welcomeMessages;
        this.playerPresence = playerPresence;
    }

    @GetMapping("/api/servers/{id}/rcon/players")
    @ResponseBody
    public RconPlayersView players(@PathVariable Long id) {
        var server = servers.get(id);
        try {
            RconPlayersView view = rcon.players(id);
            if (view.success()) {
                playerPresence.recordPresence(server, view.players(), LocalDateTime.now());
            }
            return view;
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "No se pudo consultar RCON." : e.getMessage();
            return RconPlayersView.failed(server.getId(), server.getName(), message);
        }
    }

    @PostMapping("/api/servers/{id}/rcon/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @ResponseBody
    public Map<String, Object> broadcast(@PathVariable Long id, @RequestParam String message, Principal principal) {
        var server = servers.get(id);
        var log = actionLogs.started(server, "rcon-broadcast", principal.getName());
        try {
            String response = rcon.broadcast(id, message);
            actionLogs.finish(log, new CommandResult(
                    List.of("rcon", "Broadcast"),
                    0,
                    response,
                    "",
                    Duration.ZERO,
                    false
            ));
            return Map.of("success", true, "message", response == null || response.isBlank() ? "Mensaje enviado." : response);
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage() == null ? "No se pudo enviar el mensaje RCON." : e.getMessage();
            actionLogs.finish(log, new CommandResult(
                    List.of("rcon", "Broadcast"),
                    1,
                    "",
                    errorMessage,
                    Duration.ZERO,
                    false
            ));
            return Map.of("success", false, "message", errorMessage);
        }
    }

    @GetMapping("/api/servers/{id}/rcon/welcome")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public RconWelcomeMessageService.WelcomeConfigView welcomeConfig(@PathVariable Long id) {
        return welcomeMessages.view(id);
    }

    @PutMapping("/api/servers/{id}/rcon/welcome")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<RconWelcomeMessageService.WelcomeConfigView> saveWelcomeConfig(@PathVariable Long id, @RequestBody WelcomeConfigRequest request) {
        try {
            return ResponseEntity.ok(welcomeMessages.save(id, request.enabled(), request.delaySeconds(), request.messages()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record WelcomeConfigRequest(boolean enabled, Integer delaySeconds, List<String> messages) {
    }
}
