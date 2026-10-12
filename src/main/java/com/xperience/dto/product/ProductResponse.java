package com.xperience.dto.product;

import com.xperience.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Long id, String name, String description,
                              BigDecimal price, LocalDateTime createdAt,
                              Long ownerId, Integer maxPeople)
{
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCreatedAt(),
                product.getOwner().getId(),
                product.getMaxPeople()
        );
    }
}
