package com.example.flashreserve.controller;

import com.example.flashreserve.dto.CreateProductRequest;
import com.example.flashreserve.dto.ProductResponse;
import com.example.flashreserve.dto.ReserveInventoryRequest;
import com.example.flashreserve.service.ProductService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.math.BigDecimal;
import java.util.Set;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    @Operation(
            summary = "Create a product",
            description = "Creates a new product with its initial inventory. ADMIN access required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
//    @PostMapping("/{productId}/reserve")
//    public ResponseEntity<Void>reserve(@PathVariable Long productId,@Valid @RequestBody ReserveInventoryRequest request){
//        productService.reserve(productId,request.quantity());
//        return ResponseEntity.noContent().build();
//    }
//    @GetMapping
//    public ResponseEntity<Page<ProductResponse>> getProducts(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(defaultValue = "name") String sortBy,
//            @RequestParam(defaultValue = "asc") String direction) {
//
//        if (page < 0 || size < 1 || size > 100) {
//            throw new IllegalArgumentException(
//                    "Page must be non-negative and size must be between 1 and 100"
//            );
//        }
//
//        Sort.Direction sortDirection;
//        try {
//            sortDirection = Sort.Direction.fromString(direction);
//        } catch (IllegalArgumentException ex) {
//            throw new IllegalArgumentException(
//                    "Direction must be 'asc' or 'desc'"
//            );
//        }
//
//        if (!java.util.Set.of("id", "name", "price", "createdAt")
//                .contains(sortBy)) {
//            throw new IllegalArgumentException("Unsupported sort field");
//        }
//
//        Pageable pageable = PageRequest.of(
//                page,
//                size,
//                Sort.by(sortDirection, sortBy)
//        );
//
//        return ResponseEntity.ok(productService.getProducts(pageable));
//    }
@Operation(
        summary = "Search products",
        description = "Search and filter products with pagination and sorting."
)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid search or pagination parameters")
})
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be >= 0 and size must be between 1 and 100"
            );
        }

        if (minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Prices must be non-negative"
            );
        }

        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "minPrice must be less than or equal to maxPrice"
            );
        }

        if (!Set.of("id", "name", "price", "createdAt")
                .contains(sortBy)) {
            throw new IllegalArgumentException(
                    "sortBy must be one of: id, name, price, createdAt"
            );
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "direction must be 'asc' or 'desc'"
            );
        }

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(sortDirection, sortBy)
        );

        return ResponseEntity.ok(
                productService.searchProducts(
                        search, minPrice, maxPrice, inStock, pageable
                )
        );
    }
    @Operation(
            summary = "Get product by ID",
            description = "Retrieves product details using its unique ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                productService.getProductById(productId)
        );
    }

}