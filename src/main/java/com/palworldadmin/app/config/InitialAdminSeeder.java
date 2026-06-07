package com.palworldadmin.app.config;

import com.palworldadmin.app.entity.User;
import com.palworldadmin.app.repository.UserRepository;
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

    public InitialAdminSeeder(UserRepository users, PasswordEncoder encoder, AdminProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        String username = properties.getInitialAdmin().getUsername();
        if (users.existsByUsername(username)) {
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(properties.getInitialAdmin().getPassword()));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        users.save(admin);
        log.warn("Initial admin user '{}' was created. Change PALWORLD_ADMIN_PASSWORD before production use.", username);
    }
}
