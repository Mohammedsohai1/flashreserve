
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for registering a new customer account.")
public record RegisterRequest(

        @Schema(
                description = "Customer email address. Maximum 320 characters.",
                example = "customer@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Schema(
                description = "Account password. Must contain between 12 and 72 characters.",
                example = "SecurePassword123!",
                minLength = 12,
                maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(min = 12, max = 72)
        String password
) {
}
