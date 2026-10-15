package com.xperience.service;

import com.xperience.dto.reservation.ReservationResponse;
import com.xperience.entity.*;
import com.xperience.exception.BadRequestException;
import com.xperience.exception.ForbiddenException;
import com.xperience.exception.ReservationNotFoundException;
import com.xperience.exception.SlotConflictException;
import com.xperience.exception.UserNotFoundException;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository, CartItemRepository cartItemRepository, UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<ReservationResponse> checkout(Long userId){
        List<CartItem> cart = cartItemRepository.findByUserId(userId);
        if (cart.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        LocalDateTime now = LocalDateTime.now();
        List<ReservationResponse> reservations = new ArrayList<>();
        for (CartItem item : cart) {
            TimeSlot slot = item.getTimeSlot();
            String what = slot.getProduct().getName() + " at " + slot.getStartTime();

            if (slot.getStatus() != StatusSlot.OPEN) {
                throw new SlotConflictException(what + " is no longer available");
            }
            if (!slot.getStartTime().isAfter(now)) {
                throw new SlotConflictException(what + " has already started");
            }
            if (item.getNumberPeople() > slot.getPlacesLeft()) {
                throw new SlotConflictException(what + " has only " + slot.getPlacesLeft() + " places left");
            }

            slot.setBookedPeople(slot.getBookedPeople() + item.getNumberPeople());
            if(slot.getPlacesLeft() == 0){
                slot.setStatus(StatusSlot.FULL);
            }

            Reservation reservation = new Reservation();
            reservation.setTimeSlot(slot);
            reservation.setGuest(item.getUser());
            reservation.setNumberPeople(item.getNumberPeople());
            reservation.setPricePaid(slot.getProduct().getPrice()
                    .multiply(BigDecimal.valueOf(item.getNumberPeople())));
            reservation.setStatus(ReservationStatus.CONFIRMED);

            reservations.add(ReservationResponse.from(reservationRepository.save(reservation)));
        }

        cartItemRepository.deleteByUserId(userId);
        return reservations;
    }

    @Transactional
    public ReservationResponse cancelReservation(Long currentUserId, Long reservationId){

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (!isGuest(reservation, currentUserId)) {
            throw new ForbiddenException("Only the guest who made this reservation can cancel it");
        }

        if(reservation.getStatus() == ReservationStatus.CANCELLED) {
            return ReservationResponse.from(reservation);
        }

        TimeSlot slot = reservation.getTimeSlot();
        if(!reservation.getTimeSlot().getStartTime().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("You can't cancel a reservation that has already started");
        }

        slot.setBookedPeople(slot.getBookedPeople() - reservation.getNumberPeople());

        if(slot.getStatus() == StatusSlot.FULL){
            slot.setStatus(StatusSlot.OPEN);
        }
        reservation.setStatus(ReservationStatus.CANCELLED);

        return ReservationResponse.from(reservation);

    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long currentUserId, Long reservationId){
         Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

         // the guest who booked, or the host whose experience was booked
         boolean isHost = reservation.getTimeSlot().getProduct().getOwner().getId().equals(currentUserId);
         if (!isGuest(reservation, currentUserId) && !isHost) {
             throw new ForbiddenException("You can only view your own reservations or bookings of your experiences");
         }

         return ReservationResponse.from(reservation);
    }

    // the guest is null once their account has been deleted
    private boolean isGuest(Reservation reservation, Long userId) {
        return reservation.getGuest() != null && reservation.getGuest().getId().equals(userId);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(Long userId, Pageable pageable){
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        return reservationRepository.findByGuestId(userId, pageable)
               .map(ReservationResponse::from);
    }
}
