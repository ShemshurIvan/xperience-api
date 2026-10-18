package com.xperience.service;

import com.xperience.dto.cart.CartItemRequest;
import com.xperience.dto.cart.CartItemResponse;
import com.xperience.dto.cart.CartResponse;
import com.xperience.entity.CartItem;
import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import com.xperience.exception.*;
import com.xperience.exception.conflict.SlotConflictException;
import com.xperience.exception.notfound.CartItemNotFoundException;
import com.xperience.exception.notfound.TimeSlotNotFoundException;
import com.xperience.exception.notfound.UserNotFoundException;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.TimeSlotRepository;
import com.xperience.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final TimeSlotRepository timeSlotRepository;

    public CartItemService(CartItemRepository cartItemRepository, UserRepository userRepository, TimeSlotRepository timeSlotRepository) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    @Transactional
    public CartItemResponse addToCart(Long userId, CartItemRequest cartItemRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        TimeSlot slot = timeSlotRepository.findById(cartItemRequest.timeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(cartItemRequest.timeSlotId()));

        if(slot.getStatus() != StatusSlot.OPEN){
            throw new SlotConflictException("Slot is already full or cancelled");
        }

        if(!slot.getStartTime().isAfter(LocalDateTime.now())){
            throw new BadRequestException("You can't book something that already started");
        }

        if(cartItemRequest.numberPeople() > slot.getPlacesLeft()){
            throw new SlotConflictException("Only " + slot.getPlacesLeft() + " places left");
        }

        CartItem item = cartItemRepository.findByUserIdAndTimeSlotId(userId, slot.getId())
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setUser(user);
                    newItem.setTimeSlot(slot);
                    return newItem;
                });
        item.setNumberPeople(cartItemRequest.numberPeople());

        return CartItemResponse.from(cartItemRepository.saveAndFlush(item));
    }

    public CartResponse getCart(Long userId){
        if(!userRepository.existsById(userId)){
            throw new UserNotFoundException(userId);
        }
        List<CartItemResponse> items = cartItemRepository.findByUserId(userId).stream().map(CartItemResponse::from).toList();
        BigDecimal totalPrice = items.stream().
                filter(CartItemResponse::available).
                map(CartItemResponse::lineTotal).
                reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(items,totalPrice);
    }

    @Transactional
    public void removeFromCart(Long userId, Long timeSlotId){
        CartItem item = cartItemRepository.findByUserIdAndTimeSlotId(userId, timeSlotId)
                .orElseThrow(() -> new CartItemNotFoundException(timeSlotId));

        cartItemRepository.delete(item);
    }

    @Transactional
    public void clearCart(Long userId){
        if(!userRepository.existsById(userId)){
            throw new UserNotFoundException(userId);
        }
        cartItemRepository.deleteByUserId(userId);
    }
}
