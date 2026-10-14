package com.xperience.controller;

import com.xperience.dto.cart.CartItemRequest;
import com.xperience.dto.cart.CartItemResponse;
import com.xperience.dto.cart.CartResponse;
import com.xperience.service.CartItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CartItemController {

    private final CartItemService cartItemService;
    public CartItemController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping("/users/{userId}/cart")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse addToCart(@PathVariable Long userId, @RequestBody @Valid CartItemRequest cartItem){
       return cartItemService.addToCart(userId, cartItem);
    }

    @GetMapping("/users/{userId}/cart")
    public CartResponse getCart(@PathVariable Long userId){
        return cartItemService.getCart(userId);
    }

    @DeleteMapping("/users/{userId}/cart/{timeSlotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromCart(@PathVariable Long userId, @PathVariable Long timeSlotId){
        cartItemService.removeFromCart(userId, timeSlotId);
    }
    @DeleteMapping("/users/{userId}/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@PathVariable Long userId){
        cartItemService.clearCart(userId);
    }
}
