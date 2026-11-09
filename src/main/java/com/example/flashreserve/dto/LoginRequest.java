
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials required for customer authentication.")
public record LoginRequest(

        @Schema(
                description = "Registered customer email address.",
                example = "customer@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        String email,

        @Schema(
                description = "Customer account password.",
                example = "SecurePassword123!",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String password
) {
}
