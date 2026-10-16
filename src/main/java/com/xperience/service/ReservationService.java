package com.xperience.service;

import com.xperience.dto.reservation.ReservationRequest;
import com.xperience.dto.reservation.ReservationResponse;
import com.xperience.entity.*;
import com.xperience.exception.*;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.TimeSlotRepository;
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
    private final TimeSlotRepository timeSlotRepository;

    public ReservationService(ReservationRepository reservationRepository, CartItemRepository cartItemRepository, UserRepository userRepository, TimeSlotRepository timeSlotRepository) {
        this.reservationRepository = reservationRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.timeSlotRepository = timeSlotRepository;
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
            reservations.add(reserve(item.getUser(), item.getTimeSlot(), item.getNumberPeople(), now));
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

         boolean isHost = reservation.getTimeSlot().getProduct().getOwner().getId().equals(currentUserId);
         if (!isGuest(reservation, currentUserId) && !isHost) {
             throw new ForbiddenException("You can only view your own reservations or bookings of your experiences");
         }

         return ReservationResponse.from(reservation);
    }

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

    private ReservationResponse reserve(User guest, TimeSlot timeSlot, int numberPeople, LocalDateTime now) {
        String what = timeSlot.getProduct().getName() + " at " + timeSlot.getStartTime();

        if(timeSlot.getStatus() != StatusSlot.OPEN){
            throw new SlotConflictException(what + " is no longer available");
        }
        if(!timeSlot.getStartTime().isAfter(now)){
            throw new SlotConflictException(what + " has already started");
        }
        if(numberPeople > timeSlot.getPlacesLeft()){
            throw new SlotConflictException(what + " has only " + timeSlot.getPlacesLeft() + " places left");
        }
        timeSlot.setBookedPeople(timeSlot.getBookedPeople() + numberPeople);
        if(timeSlot.getPlacesLeft() == 0){
            timeSlot.setStatus(StatusSlot.FULL);
        }

        Reservation reservation = new Reservation();
        reservation.setGuest(guest);
        reservation.setTimeSlot(timeSlot);
        reservation.setNumberPeople(numberPeople);
        reservation.setPricePaid(timeSlot.getProduct().getPrice().multiply(BigDecimal.valueOf(numberPeople)));
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return ReservationResponse.from(reservationRepository.save(reservation));
    }
    @Transactional
    public ReservationResponse bookNow(Long userId, ReservationRequest reservationRequest){
        User guest = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        TimeSlot timeSlot = timeSlotRepository.findById(reservationRequest.timeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(reservationRequest.timeSlotId()));

        ReservationResponse reservation = reserve(guest, timeSlot, reservationRequest.numberPeople(), LocalDateTime.now());

        cartItemRepository.findByUserIdAndTimeSlotId(userId, timeSlot.getId())
                .ifPresent(cartItemRepository::delete);

        return reservation;
    }
}
