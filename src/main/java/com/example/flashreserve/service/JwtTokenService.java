package com.example.flashreserve.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
//import org.springframework.security.oauth2.jose.jws.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long expiresInSeconds;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.issuer:flashreserve}") String issuer,
            @Value("${security.jwt.expiration-seconds:900}") long expiresInSeconds) {

        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be positive");
        }

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String generateToken(Authentication authentication) {
        Instant now = Instant.now();

        List<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}