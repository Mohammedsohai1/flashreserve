package com.example.flashreserve.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}