package com.example.flashreserve.service;

import com.example.flashreserve.dto.CachedProductDetails;
import com.example.flashreserve.entity.Product;
import com.example.flashreserve.exception.ProductNotFoundException;
import com.example.flashreserve.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDetailsCacheService {

    private final ProductRepository productRepository;

    public ProductDetailsCacheService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(
            cacheNames = "productDetails",
            key = "#productId"
    )
    @Transactional(readOnly = true)
    public CachedProductDetails getProductDetails(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);

        return new CachedProductDetails(
                product.getId(),
                product.getName(),
                product.getPrice()
        );
    }
}
