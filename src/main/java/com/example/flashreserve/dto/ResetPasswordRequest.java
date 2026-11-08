package com.example.flashreserve.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        String resetToken,

        @NotBlank
        @Size(min = 12, max = 72)
        String newPassword
) {
}