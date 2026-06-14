package com.webexpenses.claims.service;

import com.webexpenses.claims.entity.User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {
    // TODO: Pull from config
    private static final long TOKEN_EXPIRY_SECONDS = 3600;
    private final JwtEncoder jwtEncoder;

    public TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String issueToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("claims-api")
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_EXPIRY_SECONDS, ChronoUnit.SECONDS))
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("userId", user.getId().toString())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
