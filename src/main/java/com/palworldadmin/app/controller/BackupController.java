package com.palworldadmin.app.controller;

import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.backup.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;

@Controller
public class BackupController {
    private final BackupService backups;
    private final PalworldServerService servers;

    public BackupController(BackupService backups, PalworldServerService servers) {
        this.backups = backups;
        this.servers = servers;
    }

    @GetMapping("/servers/{id}/backups")
    public String list(@PathVariable Long id, @RequestParam(required = false) String inspect, Model model) throws IOException {
        model.addAttribute("server", servers.get(id));
        model.addAttribute("backups", backups.list(id));
        if (inspect != null && !inspect.isBlank()) {
            model.addAttribute("inspectPath", inspect);
            model.addAttribute("contents", backups.content(id, inspect));
        }
        return "backups";
    }

    @GetMapping("/servers/{id}/backups/download")
    public void download(@PathVariable Long id, @RequestParam String path, HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=palworld-backup.zip");
        backups.zip(id, path, response.getOutputStream());
    }

    @PostMapping("/servers/{id}/backups/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public String restore(@PathVariable Long id, @RequestParam String path, @RequestParam String confirmation, @RequestParam(defaultValue = "false") boolean startAfterRestore, Principal principal, RedirectAttributes redirect) {
        if (!"RESTAURAR".equals(confirmation)) {
            redirect.addFlashAttribute("error", "ConfirmaciÃ³n invÃ¡lida. Escribe RESTAURAR.");
            return "redirect:/servers/" + id + "/backups";
        }
        try {
            backups.restore(id, path, startAfterRestore, principal.getName());
            redirect.addFlashAttribute("success", "Backup restaurado.");
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/backups";
    }
}
