package com.palworldadmin.app.controller;

import com.palworldadmin.app.service.configprofile.ConfigProfileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}/config-profiles")
public class ConfigProfileController {
    private final ConfigProfileService profiles;

    public ConfigProfileController(ConfigProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ConfigProfileService.ProfileListView list(@PathVariable Long serverId) {
        return profiles.list(serverId);
    }

    @GetMapping("/{profileId}")
    public ConfigProfileService.ProfileDetailView get(@PathVariable Long serverId, @PathVariable String profileId) {
        return profiles.get(serverId, profileId);
    }

    @GetMapping("/{profileId}/diff")
    public List<ConfigProfileService.DiffEntryView> diff(@PathVariable Long serverId, @PathVariable String profileId) {
        return profiles.diff(serverId, profileId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigProfileService.ProfileDetailView create(@PathVariable Long serverId, @RequestBody ConfigProfileService.ProfileWriteRequest request, Principal principal) {
        return profiles.createFromActive(serverId, request, principal.getName());
    }

    @PutMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigProfileService.ProfileDetailView update(@PathVariable Long serverId, @PathVariable String profileId, @RequestBody ConfigProfileService.ProfileUpdateRequest request, Principal principal) {
        return profiles.update(serverId, profileId, request, principal.getName());
    }

    @PostMapping("/{profileId}/duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigProfileService.ProfileDetailView duplicate(@PathVariable Long serverId, @PathVariable String profileId, @RequestBody ConfigProfileService.DuplicateRequest request, Principal principal) {
        return profiles.duplicate(serverId, profileId, request, principal.getName());
    }

    @PostMapping("/{profileId}/apply")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigProfileService.ApplyResultView apply(@PathVariable Long serverId, @PathVariable String profileId, Principal principal) {
        return profiles.apply(serverId, profileId, principal.getName());
    }

    @PostMapping("/restore-default")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigProfileService.ApplyResultView restoreDefault(@PathVariable Long serverId, Principal principal) {
        return profiles.restoreDefault(serverId, principal.getName());
    }

    @DeleteMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long serverId, @PathVariable String profileId, Principal principal) {
        profiles.delete(serverId, profileId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{profileId}/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigProfileService.ProfileExportView> export(@PathVariable Long serverId, @PathVariable String profileId, Principal principal) {
        ConfigProfileService.ProfileExportView exported = profiles.exportProfile(serverId, profileId, principal.getName());
        String safeName = exported.name == null ? profileId : exported.name.replaceAll("[^A-Za-z0-9_-]+", "-");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(exported);
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigProfileService.ProfileDetailView importProfile(@PathVariable Long serverId, @RequestBody String rawJson, Principal principal) {
        return profiles.importProfile(serverId, rawJson, principal.getName());
    }
}
