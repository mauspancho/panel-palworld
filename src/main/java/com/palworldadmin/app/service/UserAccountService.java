package com.palworldadmin.app.service;

import com.palworldadmin.app.entity.User;
import com.palworldadmin.app.entity.UserRole;
import com.palworldadmin.app.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class UserAccountService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditLogService audit;

    public UserAccountService(UserRepository users, PasswordEncoder encoder, AuditLogService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void migrateExistingUsers() {
        users.findAll().forEach(user -> {
            boolean changed = false;
            if (!StringUtils.hasText(user.getNormalizedUsername())) {
                user.setNormalizedUsername(normalizeUsername(user.getUsername()));
                changed = true;
            }
            if (!StringUtils.hasText(user.getDisplayName())) {
                user.setDisplayName(user.getUsername());
                changed = true;
            }
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(LocalDateTime.now());
                changed = true;
            }
            if (user.getPasswordChangedAt() == null) {
                user.setPasswordChangedAt(user.getCreatedAt());
                changed = true;
            }
            if (changed) {
                users.save(user);
            }
        });
    }

    public User authenticated(String username) {
        return users.findByNormalizedUsername(normalizeUsername(username))
                .or(() -> users.findByUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }

    public List<UserView> list(String search, UserRole role, Boolean enabled) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return users.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .filter(user -> role == null || user.getRole() == role)
                .filter(user -> enabled == null || user.isEnabled() == enabled)
                .filter(user -> needle.isBlank()
                        || safe(user.getUsername()).toLowerCase(Locale.ROOT).contains(needle)
                        || safe(user.getDisplayName()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::view)
                .toList();
    }

    public UserView profile(String username) {
        return view(authenticated(username));
    }

    @Transactional
    public UserView create(CreateUserRequest request, String actor) {
        String username = require(request.username(), "El nombre de usuario es obligatorio.");
        String normalized = normalizeUsername(username);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (users.existsByNormalizedUsername(normalized)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre.");
        }

        String password = require(request.password(), "La contrasena es obligatoria.");
        if (!password.equals(request.confirmPassword())) {
            throw new IllegalArgumentException("La confirmacion de contrasena no coincide.");
        }
        validatePassword(password, username, null);

        User user = new User();
        user.setUsername(username.trim());
        user.setNormalizedUsername(normalized);
        user.setDisplayName(cleanDisplayName(request.displayName()));
        user.setEmail(cleanEmail(request.email()));
        user.setRole(request.role() == null ? UserRole.USER : request.role());
        user.setEnabled(request.enabled());
        user.setMustChangePassword(request.mustChangePassword());
        user.setPasswordHash(encoder.encode(password));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setCreatedBy(actor);
        User saved = users.save(user);
        audit.success(actor, saved.getUsername(), "USER_CREATED", "Usuario creado con rol " + saved.getRole());
        return view(saved);
    }

    @Transactional
    public UserView update(Long id, UpdateUserRequest request, String actor) {
        User user = get(id);
        UserRole oldRole = user.getRole();
        boolean oldEnabled = user.isEnabled();
        user.setDisplayName(cleanDisplayName(request.displayName()));
        user.setEmail(cleanEmail(request.email()));
        if (request.role() != null) {
            ensureCanChangeRoleOrStatus(user, request.role(), request.enabled(), actor);
            user.setRole(request.role());
        }
        ensureCanChangeRoleOrStatus(user, user.getRole(), request.enabled(), actor);
        user.setEnabled(request.enabled());
        user.setMustChangePassword(request.mustChangePassword());
        User saved = users.save(user);
        audit.success(actor, saved.getUsername(), "USER_UPDATED", "Usuario actualizado");
        if (oldRole != saved.getRole()) {
            audit.success(actor, saved.getUsername(), "ROLE_CHANGED", "Rol cambiado de " + oldRole + " a " + saved.getRole());
        }
        if (oldEnabled && !saved.isEnabled()) {
            audit.success(actor, saved.getUsername(), "USER_DISABLED", "Cuenta desactivada");
        }
        if (!oldEnabled && saved.isEnabled()) {
            audit.success(actor, saved.getUsername(), "USER_ENABLED", "Cuenta activada");
        }
        return view(saved);
    }

    @Transactional
    public UserView updateProfile(String username, ProfileUpdateRequest request) {
        User user = authenticated(username);
        user.setDisplayName(cleanDisplayName(request.displayName()));
        user.setEmail(cleanEmail(request.email()));
        User saved = users.save(user);
        audit.success(saved.getUsername(), saved.getUsername(), "PROFILE_UPDATED", "Perfil actualizado");
        return view(saved);
    }

    @Transactional
    public void changeOwnPassword(String username, ChangePasswordRequest request) {
        User user = authenticated(username);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("La contrasena actual no es correcta.");
        }
        if (!safe(request.newPassword()).equals(request.confirmPassword())) {
            throw new IllegalArgumentException("La confirmacion de contrasena no coincide.");
        }
        validatePassword(request.newPassword(), user.getUsername(), user);
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        users.save(user);
        audit.success(user.getUsername(), user.getUsername(), "PASSWORD_CHANGED", "Contrasena propia actualizada");
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request, String actor) {
        User user = get(id);
        String password = require(request.password(), "La contrasena temporal es obligatoria.");
        if (!password.equals(request.confirmPassword())) {
            throw new IllegalArgumentException("La confirmacion de contrasena no coincide.");
        }
        validatePassword(password, user.getUsername(), null);
        user.setPasswordHash(encoder.encode(password));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(request.mustChangePassword() == null || request.mustChangePassword());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        audit.success(actor, user.getUsername(), "PASSWORD_RESET", "Contrasena restablecida por administrador");
    }

    @Transactional
    public UserView unlock(Long id, String actor) {
        User user = get(id);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        User saved = users.save(user);
        audit.success(actor, saved.getUsername(), "USER_UNLOCKED", "Cuenta desbloqueada");
        return view(saved);
    }

    @Transactional
    public void loginSucceeded(String username) {
        users.findByNormalizedUsername(normalizeUsername(username)).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            users.save(user);
            audit.success(user.getUsername(), user.getUsername(), "LOGIN_SUCCESS", "Inicio de sesion exitoso");
        });
    }

    @Transactional
    public void loginFailed(String username) {
        String normalized = normalizeUsername(username);
        users.findByNormalizedUsername(normalized).ifPresentOrElse(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                audit.failed(user.getUsername(), user.getUsername(), "USER_LOCKED", "Cuenta bloqueada temporalmente por intentos fallidos");
            }
            users.save(user);
            audit.failed(user.getUsername(), user.getUsername(), "LOGIN_FAILED", "Inicio de sesion fallido");
        }, () -> audit.failed(normalized.isBlank() ? "-" : normalized, "-", "LOGIN_FAILED", "Inicio de sesion fallido"));
    }

    @Transactional
    public void logout(String username) {
        audit.success(username, username, "LOGOUT", "Sesion cerrada");
    }

    private void ensureCanChangeRoleOrStatus(User user, UserRole newRole, boolean enabled, String actor) {
        boolean removingAdminAccess = user.getRole() == UserRole.ADMIN && (newRole != UserRole.ADMIN || !enabled);
        if (!removingAdminAccess) {
            return;
        }
        long activeAdmins = users.countByRoleAndEnabledTrue(UserRole.ADMIN);
        if (activeAdmins <= 1) {
            throw new IllegalArgumentException("No se puede quitar el ultimo administrador activo.");
        }
        if (user.getUsername().equalsIgnoreCase(actor) && activeAdmins <= 1) {
            throw new IllegalArgumentException("No puedes dejarte sin acceso administrativo.");
        }
    }

    private User get(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }

    private String cleanDisplayName(String value) {
        String cleaned = require(value, "El nombre visible es obligatorio.").trim().replaceAll("\\s+", " ");
        if (cleaned.length() > 120) {
            throw new IllegalArgumentException("El nombre visible es demasiado largo.");
        }
        return cleaned;
    }

    private String cleanEmail(String value) {
        if (value == null || value.trim().isBlank()) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.length() > 180 || !EMAIL_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("El formato del correo electronico no es valido.");
        }
        return cleaned;
    }

    private void validatePassword(String password, String username, User currentUser) {
        String value = require(password, "La contrasena es obligatoria.");
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("La contrasena no debe tener espacios al inicio o al final.");
        }
        if (value.length() < 10) {
            throw new IllegalArgumentException("La contrasena debe tener minimo 10 caracteres.");
        }
        if (value.chars().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("La contrasena debe incluir al menos una letra.");
        }
        if (value.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("La contrasena debe incluir al menos un numero.");
        }
        if (normalizeUsername(value).equals(normalizeUsername(username))) {
            throw new IllegalArgumentException("La contrasena no puede ser igual al nombre de usuario.");
        }
        if (currentUser != null && encoder.matches(value, currentUser.getPasswordHash())) {
            throw new IllegalArgumentException("La nueva contrasena no puede ser igual a la actual.");
        }
    }

    private String require(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public UserView view(User user) {
        return new UserView(
                user.getId(),
                user.getDisplayName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.isLocked(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getPasswordChangedAt(),
                user.getCreatedBy()
        );
    }

    public record UserView(
            Long id,
            String displayName,
            String username,
            String email,
            UserRole role,
            boolean enabled,
            boolean locked,
            boolean mustChangePassword,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt,
            LocalDateTime passwordChangedAt,
            String createdBy
    ) {
    }

    public record CreateUserRequest(
            String displayName,
            String username,
            String password,
            String confirmPassword,
            String email,
            UserRole role,
            boolean enabled,
            boolean mustChangePassword
    ) {
    }

    public record UpdateUserRequest(
            String displayName,
            String email,
            UserRole role,
            boolean enabled,
            boolean mustChangePassword
    ) {
    }

    public record ProfileUpdateRequest(String displayName, String email) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
    }

    public record ResetPasswordRequest(String password, String confirmPassword, Boolean mustChangePassword) {
    }
}
