
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to initiate password recovery.")
public record ForgotPasswordRequest(

        @Schema(
                description = "Email address associated with the customer account.",
                example = "customer@example.com",
                maxLength = 320,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        @Size(max = 320)
        String email
) {
}
