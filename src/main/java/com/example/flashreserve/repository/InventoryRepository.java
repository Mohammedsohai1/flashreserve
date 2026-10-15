package com.example.flashreserve.repository;

import com.example.flashreserve.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory,Long> {
    @Modifying
    @Query("""
UPDATE Inventory i
SET i.availableQuantity=i.availableQuantity-:quantity
WHERE i.product.id=:productId AND i.availableQuantity>=:quantity
""")
    int decreaseQuantity(
            @Param("productId")Long productId,
            @Param("quantity")int quantity);

    @Modifying
    @Query("""
    UPDATE Inventory i
    SET i.availableQuantity = i.availableQuantity + :quantity
    WHERE i.product.id = :productId
    """)
    int increaseQuantity(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );
    Optional<Inventory> findByProduct_Id(Long productId);
}

