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

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterRequest request) {

        authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    // ---------------- FORGOT PASSWORD ----------------

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

    // ---------------- VERIFY OTP ----------------

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

    // ---------------- RESET PASSWORD ----------------

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