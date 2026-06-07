package com.palworldadmin.app.controller;

import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.configeditor.ConfigEditorService;
import com.palworldadmin.app.util.CommandResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@Controller
public class ConfigController {
    private final ConfigEditorService configs;
    private final PalworldServerService servers;

    public ConfigController(ConfigEditorService configs, PalworldServerService servers) {
        this.configs = configs;
        this.servers = servers;
    }

    @GetMapping("/servers/{id}/config")
    public String edit(@PathVariable Long id, Model model) throws IOException {
        model.addAttribute("view", configs.load(id));
        model.addAttribute("status", servers.status(servers.get(id)));
        return "config";
    }

    @PostMapping("/servers/{id}/config/copy-default")
    public String copyDefault(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            String message = saveWithServerCycle(id, principal, () -> configs.copyDefault(id));
            redirect.addFlashAttribute("success", "DefaultPalWorldSettings.ini copiado. " + message);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/config";
    }

    @PostMapping("/servers/{id}/config/form")
    public String saveForm(@PathVariable Long id, @RequestParam Map<String, String> params, Principal principal, RedirectAttributes redirect) {
        try {
            String message = saveWithServerCycle(id, principal, () -> configs.saveForm(id, params));
            redirect.addFlashAttribute("success", "Configuracion guardada. Se creo respaldo automatico si existia archivo previo. " + message);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/config";
    }

    @PostMapping("/servers/{id}/config/advanced")
    public String saveAdvanced(@PathVariable Long id, @RequestParam String content, Principal principal, RedirectAttributes redirect) {
        try {
            String message = saveWithServerCycle(id, principal, () -> configs.saveAdvanced(id, content));
            redirect.addFlashAttribute("success", "Archivo avanzado guardado. Se creo respaldo automatico si existia archivo previo. " + message);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/config";
    }

    private String saveWithServerCycle(Long id, Principal principal, ConfigWrite write) throws IOException {
        boolean wasRunning = servers.status(servers.get(id)) == ServerStatus.RUNNING;
        if (!wasRunning) {
            write.run();
            return "El servidor estaba detenido, no fue necesario reiniciarlo.";
        }

        CommandResult stop = servers.action(id, "stop", principal.getName());
        if (!stop.success()) {
            throw new IllegalStateException("No se pudo detener el servidor antes de guardar: " + stop.combinedOutput());
        }

        boolean saved = false;
        try {
            write.run();
            saved = true;
        } finally {
            CommandResult start = servers.action(id, "start", principal.getName());
            if (!start.success() && saved) {
                throw new IllegalStateException("La configuracion se guardo, pero no se pudo iniciar el servidor: " + start.combinedOutput());
            }
        }
        return "El servidor fue detenido antes de guardar e iniciado nuevamente.";
    }

    @FunctionalInterface
    private interface ConfigWrite {
        void run() throws IOException;
    }
}
