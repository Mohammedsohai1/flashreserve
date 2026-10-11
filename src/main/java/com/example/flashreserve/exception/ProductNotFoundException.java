package com.example.flashreserve.exception;

public class ProductNotFoundException extends RuntimeException{
    public ProductNotFoundException(){
        super("Product Not Found");
    }
}
