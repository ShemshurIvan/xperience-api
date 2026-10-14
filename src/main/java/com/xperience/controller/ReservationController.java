package com.xperience.controller;

import com.xperience.dto.reservation.ReservationResponse;
import com.xperience.service.ReservationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReservationController {

    private final ReservationService reservationService;
    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/users/{userId}/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReservationResponse> checkout(@PathVariable Long userId){
        return reservationService.checkout(userId);
    }

    @PatchMapping("/cancelReservation/{id}")
    public ReservationResponse cancelReservation(@PathVariable Long id){
        return reservationService.cancelReservation(id);
    }

    @GetMapping("/getReservation/{id}")
    public ReservationResponse getReservationById(@PathVariable Long id){
        return reservationService.getReservationById(id);
    }

    @GetMapping("/users/{userId}/reservations")
    public PagedModel<ReservationResponse> getReservationByUserId(@PathVariable Long userId, @PageableDefault(size = 20) Pageable pageable){
        return new PagedModel<>(reservationService.getMyReservations(userId, pageable));
    }
}
