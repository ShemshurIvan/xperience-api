package com.xperience.controller;

import com.xperience.dto.cart.CartItemRequest;
import com.xperience.dto.cart.CartItemResponse;
import com.xperience.dto.cart.CartResponse;
import com.xperience.service.CartItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// "/me" = the logged-in user, taken from the token, so nobody can use another user's cart
@RestController
public class CartItemController {

    private final CartItemService cartItemService;

    public CartItemController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping("/me/cart")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse addToCart(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid CartItemRequest cartItem){
       return cartItemService.addToCart(Long.valueOf(jwt.getSubject()), cartItem);
    }

    @GetMapping("/me/cart")
    public CartResponse getCart(@AuthenticationPrincipal Jwt jwt){
        return cartItemService.getCart(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/me/cart/{timeSlotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromCart(@AuthenticationPrincipal Jwt jwt, @PathVariable Long timeSlotId){
        cartItemService.removeFromCart(Long.valueOf(jwt.getSubject()), timeSlotId);
    }

    @DeleteMapping("/me/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@AuthenticationPrincipal Jwt jwt){
        cartItemService.clearCart(Long.valueOf(jwt.getSubject()));
    }
}