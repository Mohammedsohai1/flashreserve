
package com.example.flashreserve.controller;

import com.example.flashreserve.dto.AuthResponse;
import com.example.flashreserve.dto.ForgotPasswordRequest;
import com.example.flashreserve.dto.LoginRequest;
import com.example.flashreserve.dto.MessageResponse;
import com.example.flashreserve.dto.RegisterRequest;
import com.example.flashreserve.dto.ResetPasswordRequest;
import com.example.flashreserve.dto.ResetTokenResponse;
import com.example.flashreserve.dto.VerifyOtpRequest;
import com.example.flashreserve.service.AuthService;
import com.example.flashreserve.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService) {

        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(
            summary = "Register a new customer",
            description = "Creates a new customer account using the supplied registration details."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account registered successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid registration details."),
            @ApiResponse(responseCode = "409", description = "An account with the supplied email may already exist.")
    })
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterRequest request) {

        authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "Log in",
            description = "Authenticates a customer and returns the authentication response containing the access token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful."),
            @ApiResponse(responseCode = "400", description = "Invalid login request."),
            @ApiResponse(responseCode = "401", description = "Invalid credentials.")
    })
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    @Operation(
            summary = "Request a password reset OTP",
            description = "Requests a one-time password reset code for the supplied email address. " +
                    "The response does not reveal whether an account exists for that email."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Password reset request accepted."),
            @ApiResponse(responseCode = "400", description = "Invalid email address.")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestOtp(request.email());

        return ResponseEntity.accepted().body(
                new MessageResponse(
                        "If an account exists for this email, a password reset OTP has been sent."
                )
        );
    }

    @Operation(
            summary = "Verify password reset OTP",
            description = "Verifies the OTP sent to the supplied email address and returns a password reset token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or incorrect OTP.")
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<ResetTokenResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        ResetTokenResponse response =
                passwordResetService.verifyOtp(
                        request.email(),
                        request.otp()
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Reset account password",
            description = "Resets the password using the email address, reset token, and new password."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset token, or invalid password.")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(
                request.email(),
                request.resetToken(),
                request.newPassword()
        );

        return ResponseEntity.ok(
                new MessageResponse(
                        "Password reset successfully. Please log in with your new password."
                )
        );
    }
}
