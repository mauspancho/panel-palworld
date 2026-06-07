package com.palworldadmin.app.controller;

import com.palworldadmin.app.config.PalworldDefaultsProperties;
import com.palworldadmin.app.entity.PalworldServer;
import com.palworldadmin.app.entity.ServerType;
import com.palworldadmin.app.service.ActionLogService;
import com.palworldadmin.app.service.PalworldServerService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class DashboardController {
    private final PalworldServerService servers;
    private final ActionLogService actionLogs;
    private final PalworldDefaultsProperties defaults;

    public DashboardController(PalworldServerService servers, ActionLogService actionLogs, PalworldDefaultsProperties defaults) {
        this.servers = servers;
        this.actionLogs = actionLogs;
        this.defaults = defaults;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("rows", servers.dashboardRows());
        model.addAttribute("recentLogs", actionLogs.recentAll());
        return "dashboard";
    }

    @GetMapping("/servers/new")
    public String create(Model model) {
        PalworldServer server = new PalworldServer();
        server.setRootPath(defaults.getBasePath() + "/server01");
        server.setSteamcmdPath(defaults.getSteamcmdPath());
        server.setLinuxUser(defaults.getRunUser());
        server.setLinuxGroup(defaults.getRunGroup());
        server.setPublicPort(defaults.getPublicPort());
        model.addAttribute("server", server);
        model.addAttribute("types", ServerType.values());
        return "server-form";
    }

    @GetMapping("/servers/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("server", servers.get(id));
        model.addAttribute("types", ServerType.values());
        model.addAttribute("paths", servers.paths(id));
        return "server-form";
    }

    @PostMapping("/servers")
    public String save(@Valid @ModelAttribute("server") PalworldServer server, BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("types", ServerType.values());
            return "server-form";
        }
        try {
            PalworldServer saved = servers.save(server);
            redirect.addFlashAttribute("success", "Servidor guardado.");
            return "redirect:/servers/" + saved.getId() + "/edit";
        } catch (RuntimeException e) {
            model.addAttribute("types", ServerType.values());
            model.addAttribute("error", e.getMessage());
            return "server-form";
        }
    }

    @PostMapping("/servers/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            servers.delete(id);
            redirect.addFlashAttribute("success", "Servidor eliminado de la lista del panel.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", "No se pudo eliminar el servidor de la lista: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/servers/{id}/{action:start|stop|restart|update|install}")
    public String action(@PathVariable Long id, @PathVariable String action, Principal principal, RedirectAttributes redirect) {
        try {
            var result = servers.action(id, action, principal.getName());
            if (result.success()) {
                redirect.addFlashAttribute("success", "Accion ejecutada: " + action);
            } else {
                String output = result.combinedOutput();
                redirect.addFlashAttribute("error", output.isBlank() ? "La accion fallo." : output);
            }
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }
}
