package com.example.flashreserve.dto;

public record ResetTokenResponse(
        String resetToken,
        String message
) {
}