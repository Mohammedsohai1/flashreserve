package com.example.flashreserve.exception;

import com.example.flashreserve.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalException {
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse>handleInsufficientInventory(InsufficientInventoryException ex){
        ErrorResponse response=new ErrorResponse(
                "INSUFFICIENT_INVENTORY",ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);


    }
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException ex) {

        ErrorResponse response = new ErrorResponse(
                "PRODUCT_NOT_FOUND",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReused(
            IdempotencyKeyReusedException ex) {

        ErrorResponse response = new ErrorResponse(
                "IDEMPOTENCY_KEY_REUSED",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex) {

        ErrorResponse response = new ErrorResponse(
                "ORDER_NOT_FOUND",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ResponseEntity<ErrorResponse> handleOrderAlreadyCancelled(
            OrderAlreadyCancelledException ex) {

        ErrorResponse response = new ErrorResponse(
                "ORDER_ALREADY_CANCELLED",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        ErrorResponse error = new ErrorResponse(
                "400",
                ex.getMessage()
        );

        return ResponseEntity.badRequest().body(error);
    }
}
