package com.example.flashreserve.controller;

import com.example.flashreserve.dto.CreateProductRequest;
import com.example.flashreserve.dto.ProductResponse;
import com.example.flashreserve.dto.ReserveInventoryRequest;
import com.example.flashreserve.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Void>reserve(@PathVariable Long productId,@Valid @RequestBody ReserveInventoryRequest request){
        productService.reserve(productId,request.quantity());
        return ResponseEntity.noContent().build();
    }
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

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                productService.getProductById(productId)
        );
    }

}