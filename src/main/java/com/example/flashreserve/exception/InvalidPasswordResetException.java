package com.example.flashreserve.exception;

public class InvalidPasswordResetException extends RuntimeException {

    public InvalidPasswordResetException(String message) {
        super(message);
    }
}