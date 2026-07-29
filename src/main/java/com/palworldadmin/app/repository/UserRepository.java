package com.palworldadmin.app.repository;

import com.palworldadmin.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByNormalizedUsername(String normalizedUsername);
    boolean existsByUsername(String username);
    boolean existsByNormalizedUsername(String normalizedUsername);
    long countByRoleAndEnabledTrue(com.palworldadmin.app.entity.UserRole role);
}
