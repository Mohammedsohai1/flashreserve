package com.example.flashreserve.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "OTP must contain exactly 6 digits")
        String otp
) {
}
