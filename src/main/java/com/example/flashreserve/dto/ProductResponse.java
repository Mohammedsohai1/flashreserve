
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "Product information returned by the API")
public record ProductResponse(

        @Schema(
                description = "Unique product identifier",
                example = "101"
        )
        Long id,

        @Schema(
                description = "Product name",
                example = "iPhone 17 Pro"
        )
        String name,

        @Schema(
                description = "Product price",
                example = "1299.99"
        )
        BigDecimal price,

        @Schema(
                description = "Currently available inventory quantity",
                example = "100"
        )
        Integer availableQuantity

) implements Serializable {
}
