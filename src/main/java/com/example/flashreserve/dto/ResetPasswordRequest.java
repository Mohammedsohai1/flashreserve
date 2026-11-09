
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for resetting a customer account password.")
public record ResetPasswordRequest(

        @Schema(
                description = "Email address associated with the account.",
                example = "customer@example.com",
                maxLength = 320,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Schema(
                description = "Temporary reset token obtained after OTP verification.",
                example = "sample-reset-token",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String resetToken,

        @Schema(
                description = "New account password. Must contain between 12 and 72 characters.",
                example = "NewSecurePassword123!",
                minLength = 12,
                maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(min = 12, max = 72)
        String newPassword
) {
}