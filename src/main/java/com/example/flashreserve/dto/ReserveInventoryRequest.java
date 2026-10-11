package com.example.flashreserve.dto;

import jakarta.validation.constraints.Positive;

public record ReserveInventoryRequest (
        @Positive
        Integer quantity
){
}
