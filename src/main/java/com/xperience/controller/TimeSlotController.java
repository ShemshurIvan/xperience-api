package com.xperience.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.xperience.dto.timeslot.TimeSlotRequest;
import com.xperience.dto.timeslot.TimeSlotResponse;
import com.xperience.service.TimeSlotService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Time slots", description = "Bookable sessions of an experience")
@RestController
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @Operation(summary = "Open a time slot",
               description = "Only the experience's owner. The end time is calculated from the experience's duration; slots can't overlap.")
    @PostMapping("/createTimeSlot/{productId}")
    public ResponseEntity<TimeSlotResponse> createTimeSlot(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable Long productId,
                                                           @RequestBody @Valid TimeSlotRequest timeSlotRequest) {
        TimeSlotResponse response = timeSlotService.createTimeSlot(Long.valueOf(jwt.getSubject()), productId, timeSlotRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Calendar for a date range",
               description = "Slots of one experience starting between from and to, earliest first. No account needed.")
    @SecurityRequirements
    @GetMapping("/listTimeSlots")
    public PagedModel<TimeSlotResponse> listTimeSlots(@RequestParam Long productId,
                                                      @RequestParam LocalDateTime from,
                                                      @RequestParam LocalDateTime to,
                                                      @PageableDefault(size = 50) Pageable pageable) {
        return new PagedModel<>(timeSlotService.listTimeSlots(productId, from, to, pageable));
    }

    @Operation(summary = "Change a slot's start time or capacity",
               description = "A booked slot can't be moved, and capacity can't drop below the people already booked.")
    @PutMapping("/updateTimeSlot/{id}")
    public ResponseEntity<TimeSlotResponse> updateSlot(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody TimeSlotRequest timeSlotRequest) {
        TimeSlotResponse timeSlotResponse = timeSlotService.updateTimeSlot(Long.valueOf(jwt.getSubject()), id, timeSlotRequest);
        return ResponseEntity.ok(timeSlotResponse);
    }

    @Operation(summary = "Cancel a slot",
               description = "Also cancels its bookings and removes it from guests' carts.")
    @PatchMapping("/cancelTimeSlot/{id}")
    public TimeSlotResponse cancelTimeSlot(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return timeSlotService.cancelTimeSlot(Long.valueOf(jwt.getSubject()), id);
    }

    @Operation(summary = "Get one time slot")
    @SecurityRequirements
    @GetMapping("/getTimeSlot/{id}")
    public TimeSlotResponse getTimeSlotById(@PathVariable Long id) {
        return timeSlotService.getTimeSlotById(id);
    }

    @Operation(summary = "Delete a slot",
               description = "Only slots without bookings; otherwise cancel it instead.")
    @DeleteMapping("/deleteTimeSlot/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeSlotById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        timeSlotService.deleteTimeSlot(Long.valueOf(jwt.getSubject()), id);
    }
}