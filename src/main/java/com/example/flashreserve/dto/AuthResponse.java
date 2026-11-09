
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing the JWT access token.")
public record AuthResponse(

        @Schema(
                description = "JWT access token used to authenticate protected API requests.",
                example = "eyJhbGciOiJIUzI1NiJ9.example.signature"
        )
        String accessToken,

        @Schema(
                description = "Authentication token type.",
                example = "Bearer"
        )
        String tokenType,

        @Schema(
                description = "Access token validity duration in seconds.",
                example = "3600"
        )
        long expiresInSeconds
) {
}
