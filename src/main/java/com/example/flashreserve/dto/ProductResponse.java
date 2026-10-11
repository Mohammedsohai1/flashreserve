package com.example.flashreserve.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductResponse (
        Long id,
        String name,
        BigDecimal price,
        Integer availableQuantity

){
}
