package com.xperience.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Long id, String name, String description,
                              BigDecimal price, LocalDateTime createdAt,
                              Long ownerId, Integer maxPeople)
{}
