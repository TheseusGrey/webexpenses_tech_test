package com.webexpenses.claims.config;

import com.webexpenses.claims.entity.Role;
import com.webexpenses.claims.entity.User;
import com.webexpenses.claims.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with initial users on application startup.
 *
 * Uses the same PasswordEncoder bean (BCrypt) to hash passwords consistently,
 * ensuring the hashes in the DB always match the encoder's algorithm/cost factor.
 *
 * Idempotent: skips users that already exist (checked by username).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUser("john.smith", "Password123!", Role.EMPLOYEE);
        seedUser("jane.doe", "Password456!", Role.EMPLOYEE);
        seedUser("mike.approver", "ApproverPass1!", Role.APPROVER);
    }

    private void seedUser(String username, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .build();
            userRepository.save(user);
        }
    }
}
