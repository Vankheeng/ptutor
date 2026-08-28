package com.ptutor.backend.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.ptutor.backend.auth.dto.UserRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    @Value("${app.security.jwt.access-token-ttl:3600}")
    private long accessTokenTtlSeconds;

    @Value("${app.security.jwt.issuer:ptutor-api}")
    private String issuer;

    public String createAccessToken(UUID userId, String email, UserRole role) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessTokenTtlSeconds, ChronoUnit.SECONDS))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
