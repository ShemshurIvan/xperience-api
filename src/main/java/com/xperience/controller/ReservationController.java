package com.xperience.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.xperience.dto.reservation.ReservationRequest;
import com.xperience.dto.reservation.ReservationResponse;
import com.xperience.service.ReservationService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Reservations", description = "Bookings")
@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "Book everything in your cart",
               description = "All or nothing: if one slot is no longer available, nothing is booked and the cart stays as it was.")
    @PostMapping("/me/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReservationResponse> checkout(@AuthenticationPrincipal Jwt jwt){
        return reservationService.checkout(Long.valueOf(jwt.getSubject()));
    }

    @Operation(summary = "Cancel your booking",
               description = "Only the guest who booked. The places go back to the slot.")
    @PatchMapping("/cancelReservation/{id}")
    public ReservationResponse cancelReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id){
        return reservationService.cancelReservation(Long.valueOf(jwt.getSubject()), id);
    }

    @Operation(summary = "Get one booking",
               description = "Visible to the guest who booked and to the experience's seller.")
    @GetMapping("/getReservation/{id}")
    public ReservationResponse getReservationById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id){
        return reservationService.getReservationById(Long.valueOf(jwt.getSubject()), id);
    }

    @Operation(summary = "Your bookings",
               description = "Newest first, paged.")
    @GetMapping("/me/reservations")
    public PagedModel<ReservationResponse> getMyReservations(@AuthenticationPrincipal Jwt jwt,
                                                             @ParameterObject @PageableDefault(size = 20) Pageable pageable){
        return new PagedModel<>(reservationService.getMyReservations(Long.valueOf(jwt.getSubject()), pageable));
    }
    @Operation(summary = "Book one slot now, without the cart",
               description = "Same checks as checkout. If the slot was in your cart, it is removed from there.")
    @PostMapping("/me/reserveNow")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveNow(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody ReservationRequest reservationRequest){
        return reservationService.bookNow(Long.valueOf(jwt.getSubject()), reservationRequest);
    }
}