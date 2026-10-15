package com.xperience.controller;

import com.xperience.dto.reservation.ReservationResponse;
import com.xperience.service.ReservationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/me/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReservationResponse> checkout(@AuthenticationPrincipal Jwt jwt){
        return reservationService.checkout(Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/cancelReservation/{id}")
    public ReservationResponse cancelReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id){
        return reservationService.cancelReservation(Long.valueOf(jwt.getSubject()), id);
    }

    @GetMapping("/getReservation/{id}")
    public ReservationResponse getReservationById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id){
        return reservationService.getReservationById(Long.valueOf(jwt.getSubject()), id);
    }

    @GetMapping("/me/reservations")
    public PagedModel<ReservationResponse> getMyReservations(@AuthenticationPrincipal Jwt jwt,
                                                             @PageableDefault(size = 20) Pageable pageable){
        return new PagedModel<>(reservationService.getMyReservations(Long.valueOf(jwt.getSubject()), pageable));
    }
}