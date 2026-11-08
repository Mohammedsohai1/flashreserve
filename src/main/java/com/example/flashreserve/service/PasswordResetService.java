package com.example.flashreserve.service;

import com.example.flashreserve.dto.ResetTokenResponse;
import com.example.flashreserve.entity.AppUser;
import com.example.flashreserve.exception.InvalidPasswordResetException;
import com.example.flashreserve.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetService.class);

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final int MAX_OTP_ATTEMPTS = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> CONSUME_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    else
                        return 0
                    end
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderNotificationService notificationService;

    public PasswordResetService(
            StringRedisTemplate redisTemplate,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OrderNotificationService notificationService) {

        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    // ---------------- FORGOT PASSWORD ----------------

    public void requestOtp(String email) {

        String normalizedEmail = normalizeEmail(email);

        Optional<AppUser> user =
                userRepository.findByEmailIgnoreCase(normalizedEmail);

        // Do not reveal whether the email is registered.
        if (user.isEmpty()) {
            return;
        }

        String emailHash = sha256(normalizedEmail);

        String otpKey = "password-reset:otp:" + emailHash;
        String attemptsKey = "password-reset:attempts:" + emailHash;
        String cooldownKey = "password-reset:cooldown:" + emailHash;

        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", RESEND_COOLDOWN);

        // A request was already made within the cooldown period.
        if (!Boolean.TRUE.equals(allowed)) {
            return;
        }

        String otp = generateOtp();

        redisTemplate.opsForValue()
                .set(
                        otpKey,
                        passwordEncoder.encode(otp),
                        OTP_TTL
                );

        redisTemplate.delete(attemptsKey);

        try {
            notificationService.sendPasswordResetOtp(
                    normalizedEmail,
                    otp
            );
        } catch (MailException ex) {

            log.error("Unable to send password reset email", ex);

            redisTemplate.delete(otpKey);
            redisTemplate.delete(cooldownKey);

            throw new IllegalStateException(
                    "Unable to send password reset email. Please try again."
            );
        }
    }

    // ---------------- VERIFY OTP ----------------

    public ResetTokenResponse verifyOtp(String email, String otp) {

        String normalizedEmail = normalizeEmail(email);
        String emailHash = sha256(normalizedEmail);

        String otpKey = "password-reset:otp:" + emailHash;
        String attemptsKey = "password-reset:attempts:" + emailHash;

        String storedOtpHash =
                redisTemplate.opsForValue().get(otpKey);

        if (storedOtpHash == null) {
            throw new InvalidPasswordResetException(
                    "OTP is invalid or expired"
            );
        }

        Long attempts =
                redisTemplate.opsForValue().increment(attemptsKey);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, OTP_TTL);
        }

        if (attempts == null || attempts > MAX_OTP_ATTEMPTS) {

            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);

            throw new InvalidPasswordResetException(
                    "Maximum OTP verification attempts exceeded"
            );
        }

        if (!passwordEncoder.matches(otp, storedOtpHash)) {

            if (attempts >= MAX_OTP_ATTEMPTS) {
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptsKey);

                throw new InvalidPasswordResetException(
                        "Maximum OTP verification attempts exceeded"
                );
            }

            throw new InvalidPasswordResetException(
                    "OTP is invalid or expired"
            );
        }

        // OTP can only be used once.
        redisTemplate.delete(List.of(otpKey, attemptsKey));

        String resetToken = generateResetToken();

        String resetTokenKey =
                "password-reset:token:" + emailHash;

        redisTemplate.opsForValue().set(
                resetTokenKey,
                sha256(resetToken),
                RESET_TOKEN_TTL
        );

        return new ResetTokenResponse(
                resetToken,
                "OTP verified successfully. You can now reset your password."
        );
    }

    // ---------------- RESET PASSWORD ----------------

    @Transactional
    public void resetPassword(
            String email,
            String resetToken,
            String newPassword) {

        String normalizedEmail = normalizeEmail(email);
        String emailHash = sha256(normalizedEmail);

        AppUser user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() ->
                        new InvalidPasswordResetException(
                                "Invalid or expired password reset token"
                        )
                );

        String resetTokenKey =
                "password-reset:token:" + emailHash;

        String expectedHash = sha256(resetToken);

        // Atomically compare and delete the reset token.
        Long consumed = redisTemplate.execute(
                CONSUME_TOKEN_SCRIPT,
                List.of(resetTokenKey),
                expectedHash
        );

        if (consumed == null || consumed != 1L) {
            throw new InvalidPasswordResetException(
                    "Invalid or expired password reset token"
            );
        }

        user.updatePasswordHash(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);
    }

    // ---------------- HELPERS ----------------

    private String generateOtp() {

        int number = SECURE_RANDOM.nextInt(1_000_000);

        return String.format(Locale.ROOT, "%06d", number);
    }

    private String generateResetToken() {

        byte[] bytes = new byte[32];

        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to process password reset token",
                    ex
            );
        }
    }
}