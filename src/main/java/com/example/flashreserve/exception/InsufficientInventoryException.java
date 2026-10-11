package com.example.flashreserve.exception;

public class InsufficientInventoryException extends RuntimeException{
    public InsufficientInventoryException(){
        super("Insufficient inventory");
    }
}
