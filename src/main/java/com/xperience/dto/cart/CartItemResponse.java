package com.xperience.dto.cart;

import com.xperience.entity.CartItem;
import com.xperience.entity.Product;
import com.xperience.entity.TimeSlot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartItemResponse(Long id,
                               Long timeSlotId,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               Long productId,
                               String productName,
                               Integer numberPeople,
                               BigDecimal pricePerPerson,
                               BigDecimal lineTotal,
                               boolean available,
                               LocalDateTime addedAt) {

    public static CartItemResponse from(CartItem item) {
        TimeSlot slot = item.getTimeSlot();
        Product product = slot.getProduct();
        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getNumberPeople()));

        return new CartItemResponse(
                item.getId(),
                slot.getId(),
                slot.getStartTime(),
                slot.getEndTime(),
                product.getId(),
                product.getName(),
                item.getNumberPeople(),
                product.getPrice(),
                lineTotal,
                slot.getPlacesLeft() >= item.getNumberPeople(),
                item.getAddedAt()
        );
    }
}