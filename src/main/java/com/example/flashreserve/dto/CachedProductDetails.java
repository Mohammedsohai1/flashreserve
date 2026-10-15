package com.example.flashreserve.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record CachedProductDetails (
        Long id,
        String name,
        BigDecimal price

) implements Serializable {
}
