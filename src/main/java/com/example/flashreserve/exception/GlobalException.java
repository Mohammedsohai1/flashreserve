package com.example.flashreserve.exception;

import com.example.flashreserve.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalException {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalException.class);

    // ---------------- AUTHENTICATION ----------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex) {

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password"
        );
    }

    // ---------------- VALIDATION ----------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                message
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                ex.getMessage()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestBody(
            HttpMessageNotReadableException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or contains invalid JSON"
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Required parameter is missing: " + ex.getParameterName()
        );
    }

    // ---------------- EXISTING BUSINESS EXCEPTIONS ----------------

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventory(
            InsufficientInventoryException ex) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_INVENTORY",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException ex) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReused(
            IdempotencyKeyReusedException ex) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                ex.getMessage()
        );
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ResponseEntity<ErrorResponse> handleOrderAlreadyCancelled(
            OrderAlreadyCancelledException ex) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "ORDER_ALREADY_CANCELLED",
                ex.getMessage()
        );
    }

    // ---------------- GENERAL APPLICATION EXCEPTIONS ----------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());

        String message = ex.getReason() != null
                ? ex.getReason()
                : status.getReasonPhrase();

        return buildResponse(
                status,
                status.name(),
                message
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseConflict(
            DataIntegrityViolationException ex) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                "A record with the supplied information already exists"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                ex.getMessage()
        );
    }

    // ---------------- UNEXPECTED EXCEPTIONS ----------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex) {

        log.error("Unexpected application error", ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred"
        );
    }

    // ---------------- COMMON RESPONSE BUILDER ----------------

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message) {

        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message));
    }
    @ExceptionHandler(InvalidPasswordResetException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPasswordReset(
            InvalidPasswordResetException ex) {

        ErrorResponse response = new ErrorResponse(
                "INVALID_PASSWORD_RESET",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}