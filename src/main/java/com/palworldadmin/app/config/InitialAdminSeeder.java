package com.palworldadmin.app.config;

import com.palworldadmin.app.entity.User;
import com.palworldadmin.app.repository.UserRepository;
import com.palworldadmin.app.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitialAdminSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AdminProperties properties;
    private final UserAccountService accounts;

    public InitialAdminSeeder(UserRepository users, PasswordEncoder encoder, AdminProperties properties, UserAccountService accounts) {
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
        this.accounts = accounts;
    }

    @Override
    public void run(String... args) {
        accounts.migrateExistingUsers();
        String username = properties.getInitialAdmin().getUsername();
        if (users.existsByNormalizedUsername(accounts.normalizeUsername(username))) {
            return;
        }
        User admin = new User();
        admin.setUsername(username.trim());
        admin.setNormalizedUsername(accounts.normalizeUsername(username));
        admin.setDisplayName(username.trim());
        admin.setPasswordHash(encoder.encode(properties.getInitialAdmin().getPassword()));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setPasswordChangedAt(java.time.LocalDateTime.now());
        admin.setCreatedBy("system");
        users.save(admin);
        log.warn("Initial admin user '{}' was created. Change PALWORLD_ADMIN_PASSWORD before production use.", username);
    }
}
