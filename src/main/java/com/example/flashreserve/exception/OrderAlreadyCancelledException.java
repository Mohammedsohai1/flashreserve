package com.example.flashreserve.exception;

public class OrderAlreadyCancelledException extends RuntimeException{
    public OrderAlreadyCancelledException(){
        super("Order has already been cancelled ");
    }
}
