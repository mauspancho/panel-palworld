package com.palworldadmin.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palworldadmin.app.entity.User;
import com.palworldadmin.app.entity.UserRole;
import com.palworldadmin.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-management-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class UserManagementControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        users.save(user("admin", "Admin Principal", UserRole.ADMIN, true));
        users.save(user("operador", "Operador", UserRole.USER, true));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreatesUserAndDuplicateUsernameIsNormalized() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "displayName", "Mauricio Romero",
                                "username", " Mauricio ",
                                "password", "Temporal123",
                                "confirmPassword", "Temporal123",
                                "email", "mauricio@example.com",
                                "role", "USER",
                                "enabled", true,
                                "mustChangePassword", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mauricio Romero")))
                .andExpect(content().string(not(containsString("Temporal123"))));

        assertTrue(users.existsByNormalizedUsername("mauricio"));

        mvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "displayName", "Duplicado",
                                "username", "MAURICIO",
                                "password", "Temporal123",
                                "confirmPassword", "Temporal123",
                                "role", "USER",
                                "enabled", true,
                                "mustChangePassword", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Ya existe")));
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void regularUserCannotManageUsers() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void userUpdatesOwnProfileAndChangesPassword() throws Exception {
        mvc.perform(put("/api/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "displayName", "Operador Actualizado",
                                "email", "operador@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Operador Actualizado")));

        mvc.perform(post("/api/profile/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "currentPassword", "Inicial123",
                                "newPassword", "NuevaClave123",
                                "confirmPassword", "NuevaClave123"
                        ))))
                .andExpect(status().isOk());

        User updated = users.findByNormalizedUsername("operador").orElseThrow();
        assertEquals("Operador Actualizado", updated.getDisplayName());
        assertTrue(encoder.matches("NuevaClave123", updated.getPasswordHash()));
    }

    @Test
    @WithMockUser(username = "operador", roles = "USER")
    void wrongCurrentPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/profile/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "currentPassword", "Incorrecta123",
                                "newPassword", "NuevaClave123",
                                "confirmPassword", "NuevaClave123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("La contrasena actual no es correcta")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void lastActiveAdminCannotBeDisabled() throws Exception {
        User admin = users.findByNormalizedUsername("admin").orElseThrow();
        mvc.perform(put("/api/admin/users/" + admin.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "displayName", "Admin Principal",
                                "email", "",
                                "role", "ADMIN",
                                "enabled", false,
                                "mustChangePassword", false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("ultimo administrador")));
    }

    private User user(String username, String displayName, UserRole role, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setNormalizedUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(encoder.encode("Inicial123"));
        user.setRole(role);
        user.setEnabled(enabled);
        user.setCreatedAt(LocalDateTime.now());
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setCreatedBy("test");
        return user;
    }
}
