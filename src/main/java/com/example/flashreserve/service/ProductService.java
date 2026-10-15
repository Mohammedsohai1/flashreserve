package com.example.flashreserve.service;

import com.example.flashreserve.dto.CreateProductRequest;
import com.example.flashreserve.dto.ProductResponse;
import com.example.flashreserve.entity.Inventory;
import com.example.flashreserve.entity.Product;
import com.example.flashreserve.exception.InsufficientInventoryException;
import com.example.flashreserve.exception.ProductNotFoundException;
import com.example.flashreserve.repository.InventoryRepository;
import com.example.flashreserve.repository.ProductRepository;
import com.example.flashreserve.repository.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductDetailsCacheService productDetailsCacheService;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          ProductDetailsCacheService productDetailsCacheService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.productDetailsCacheService = productDetailsCacheService;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {

        Product product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());
        product.setCreatedAt(LocalDateTime.now());

        Product savedProduct = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(savedProduct);
        inventory.setAvailableQuantity(request.initialQuantity());

        Inventory savedInventory = inventoryRepository.save(inventory);

        savedProduct.setInventory(savedInventory);

        return new ProductResponse(
                savedProduct.getId(),
                savedProduct.
                        getName(),
                savedProduct.getPrice(),
                savedInventory.getAvailableQuantity()
        );
    }
    @Transactional
    public void reserve(Long productId, int quantity){
        int updatedRows=inventoryRepository.decreaseQuantity(productId,quantity);
        if(updatedRows==0){
            throw new InsufficientInventoryException();
        }
    }
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getInventory().getAvailableQuantity()
                ));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        var details = productDetailsCacheService.getProductDetails(productId);

        Inventory inventory = inventoryRepository.findByProduct_Id(productId)
                .orElseThrow(ProductNotFoundException::new);

        return new ProductResponse(
                details.id(),
                details.name(),
                details.price(),
                inventory.getAvailableQuantity()
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Pageable pageable) {

        Specification<Product> specification =
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.conjunction();

        if (search != null && !search.isBlank()) {
            specification = specification.and(
                    ProductSpecifications.nameContains(search.trim())
            );
        }

        if (minPrice != null) {
            specification = specification.and(
                    ProductSpecifications.priceGreaterThanOrEqualTo(minPrice)
            );
        }

        if (maxPrice != null) {
            specification = specification.and(
                    ProductSpecifications.priceLessThanOrEqualTo(maxPrice)
            );
        }

        if (Boolean.TRUE.equals(inStock)) {
            specification = specification.and(
                    ProductSpecifications.inStock()
            );
        }

        return productRepository.findAll(specification, pageable)
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getInventory().getAvailableQuantity()
                ));
    }
}
