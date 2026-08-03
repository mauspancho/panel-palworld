package com.palworldadmin.app.controller;

import com.palworldadmin.app.dto.ConfigField;
import com.palworldadmin.app.entity.ServerStatus;
import com.palworldadmin.app.service.PalworldServerService;
import com.palworldadmin.app.service.configeditor.ConfigEditorService;
import com.palworldadmin.app.service.configprofile.ConfigProfileService;
import com.palworldadmin.app.util.CommandResult;
import com.palworldadmin.app.util.IniParser;
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
import java.util.List;
import java.util.Map;

@Controller
public class ConfigController {
    private final ConfigEditorService configs;
    private final PalworldServerService servers;
    private final ConfigProfileService profiles;

    public ConfigController(ConfigEditorService configs, PalworldServerService servers, ConfigProfileService profiles) {
        this.configs = configs;
        this.servers = servers;
        this.profiles = profiles;
    }

    @GetMapping("/servers/{id}/config")
    public String edit(@PathVariable Long id, @RequestParam(required = false) String profileId, Model model) throws IOException {
        ConfigEditorService.ConfigView view = configs.load(id);
        ConfigProfileService.ProfileListView profileList;
        try {
            profileList = profiles.list(id);
        } catch (RuntimeException e) {
            profileList = new ConfigProfileService.ProfileListView(id, view.server().getName(), null, null, false, List.of());
        }
        ConfigProfileService.ProfileDetailView selectedProfile = null;
        if (profileId != null && !profileId.isBlank()) {
            selectedProfile = profiles.get(id, profileId);
            List<ConfigField> fields = IniParser.parseOptionSettings(selectedProfile.configuration()).entrySet().stream()
                    .map(entry -> new ConfigField(entry.getKey(), entry.getValue()))
                    .toList();
            view = new ConfigEditorService.ConfigView(view.server(), view.paths(), selectedProfile.configuration(), fields, true, view.defaultSettingsExists(), view.serverStatus());
        }
        model.addAttribute("view", view);
        model.addAttribute("configProfiles", profileList);
        model.addAttribute("selectedConfigProfile", selectedProfile);
        model.addAttribute("status", servers.status(servers.get(id)));
        return "config";
    }

    @PostMapping("/servers/{id}/config/copy-default")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public String saveAdvanced(@PathVariable Long id, @RequestParam String content, Principal principal, RedirectAttributes redirect) {
        try {
            String message = saveWithServerCycle(id, principal, () -> configs.saveAdvanced(id, content));
            redirect.addFlashAttribute("success", "Archivo avanzado guardado. Se creo respaldo automatico si existia archivo previo. " + message);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/config";
    }

    @PostMapping("/servers/{id}/config/profiles/{profileId}/form")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveProfileForm(@PathVariable Long id, @PathVariable String profileId, @RequestParam Map<String, String> params, Principal principal, RedirectAttributes redirect) {
        try {
            profiles.updateParameters(id, profileId, new ConfigProfileService.ProfileParameterUpdateRequest(params), principal.getName());
            redirect.addFlashAttribute("success", "Parametros del perfil guardados. No se modifico el PalWorldSettings.ini activo.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/config?profileId=" + profileId;
    }

    @PostMapping("/servers/{id}/config/profiles/{profileId}/advanced")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveProfileAdvanced(@PathVariable Long id, @PathVariable String profileId, @RequestParam String content, Principal principal, RedirectAttributes redirect) {
        try {
            ConfigProfileService.ProfileDetailView current = profiles.get(id, profileId);
            profiles.update(id, profileId, new ConfigProfileService.ProfileUpdateRequest(current.name(), current.description(), content), principal.getName());
            redirect.addFlashAttribute("success", "Contenido avanzado del perfil guardado. No se modifico el PalWorldSettings.ini activo.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id + "/config?profileId=" + profileId;
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
