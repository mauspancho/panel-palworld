package com.palworldadmin.app.controller;

import com.palworldadmin.app.entity.ActionStatus;
import com.palworldadmin.app.entity.AuditLog;
import com.palworldadmin.app.entity.UserRole;
import com.palworldadmin.app.service.AuditLogService;
import com.palworldadmin.app.service.UserAccountService;
import com.palworldadmin.app.service.UserAccountService.ChangePasswordRequest;
import com.palworldadmin.app.service.UserAccountService.CreateUserRequest;
import com.palworldadmin.app.service.UserAccountService.ProfileUpdateRequest;
import com.palworldadmin.app.service.UserAccountService.ResetPasswordRequest;
import com.palworldadmin.app.service.UserAccountService.UpdateUserRequest;
import com.palworldadmin.app.service.UserAccountService.UserView;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class UserManagementController {
    private final UserAccountService accounts;
    private final AuditLogService audit;

    public UserManagementController(UserAccountService accounts, AuditLogService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @GetMapping("/profile")
    public UserView profile(Principal principal) {
        return accounts.profile(principal.getName());
    }

    @PutMapping("/profile")
    public UserView updateProfile(@RequestBody ProfileUpdateRequest request, Principal principal) {
        return accounts.updateProfile(principal.getName(), request);
    }

    @PostMapping("/profile/password")
    public ResponseEntity<MessageView> changePassword(@RequestBody ChangePasswordRequest request, Principal principal) {
        accounts.changeOwnPassword(principal.getName(), request);
        return ResponseEntity.ok(new MessageView("Contrasena actualizada."));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean enabled
    ) {
        return accounts.list(search, role, enabled);
    }

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView create(@RequestBody CreateUserRequest request, Principal principal) {
        return accounts.create(request, principal.getName());
    }

    @PutMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView update(@PathVariable Long id, @RequestBody UpdateUserRequest request, Principal principal) {
        return accounts.update(id, request, principal.getName());
    }

    @PostMapping("/admin/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageView> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request, Principal principal) {
        accounts.resetPassword(id, request, principal.getName());
        return ResponseEntity.ok(new MessageView("Contrasena restablecida."));
    }

    @PostMapping("/admin/users/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView unlock(@PathVariable Long id, Principal principal) {
        return accounts.unlock(id, principal.getName());
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedAuditView audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Page<AuditLog> logs = audit.recent(page, size);
        return new PagedAuditView(
                logs.getContent().stream().map(AuditView::from).toList(),
                new ApiController.PageView(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageView> validation(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new MessageView(e.getMessage()));
    }

    public record MessageView(String message) {
    }

    public record PagedAuditView(List<AuditView> items, ApiController.PageView page) {
    }

    public record AuditView(
            LocalDateTime createdAt,
            String actorUsername,
            String targetUsername,
            String action,
            ActionStatus status,
            String description
    ) {
        static AuditView from(AuditLog log) {
            return new AuditView(
                    log.getCreatedAt(),
                    log.getActorUsername(),
                    log.getTargetUsername(),
                    log.getAction(),
                    log.getStatus(),
                    log.getDescription()
            );
        }
    }
}
