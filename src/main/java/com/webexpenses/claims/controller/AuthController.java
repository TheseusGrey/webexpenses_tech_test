package com.webexpenses.claims.controller;

import com.webexpenses.claims.dto.LoginRequest;
import com.webexpenses.claims.dto.LoginResponse;
import com.webexpenses.claims.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller.
 *
 * Endpoint: POST /api/auth/login
 *
 * Design decisions:
 * - Only accepts JSON (Content-Type: application/json)
 * - Uses @Valid for input validation (triggers 400 on blank fields)
 * - Returns consistent error shape on all failure modes
 * - No GET mapping (login is a state-changing action)
 *
 * Suggested error handling approach:
 * - BadCredentialsException -> 401 with generic message
 * - MethodArgumentNotValidException -> 400 with field errors
 * - HttpMessageNotReadableException -> 400 (malformed JSON)
 * - All other -> 500 with no internal details leaked
 *
 * Consider: A @RestControllerAdvice for centralised exception handling
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticate user credentials and return a JWT.
     */
    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(new LoginResponse(token, "Bearer"));
    }
}
