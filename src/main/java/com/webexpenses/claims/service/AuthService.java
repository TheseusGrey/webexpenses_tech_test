package com.webexpenses.claims.service;

import com.webexpenses.claims.dto.LoginRequest;
import com.webexpenses.claims.dto.LoginResponse;
import com.webexpenses.claims.entity.User;
import com.webexpenses.claims.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Authentication service responsible for validating credentials.
 */
@Service
public class AuthService {
    // You'd probably want to use an error code rather than plain english for production systems here
    private static final String GENERIC_AUTH_ERROR = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;


    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /**
     * Checks the login details against the db, issues a new encoded
     * token if credentials come back okay
     * @param request
     * @return new (encoded) token representing the user's session
     */
    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_AUTH_ERROR));

        // Compare password to hashed value in db
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException(GENERIC_AUTH_ERROR);
        }

        return tokenService.issueToken(user);
    }
}
