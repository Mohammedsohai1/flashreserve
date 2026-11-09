
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for verifying a password reset OTP.")
public record VerifyOtpRequest(

        @Schema(
                description = "Email address to which the OTP was sent.",
                example = "customer@example.com",
                maxLength = 320,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Schema(
                description = "Six-digit one-time password received by email.",
                example = "123456",
                minLength = 6,
                maxLength = 6,
                pattern = "^\\d{6}$",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "OTP must contain exactly 6 digits")
        String otp
) {
}
