package com.xperience.controller;

import com.xperience.dto.timeslot.TimeSlotRequest;
import com.xperience.dto.timeslot.TimeSlotResponse;
import com.xperience.service.TimeSlotService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @PostMapping("/createTimeSlot/{productId}")
    public ResponseEntity<TimeSlotResponse> createTimeSlot(@PathVariable Long productId,
                                                           @RequestBody @Valid TimeSlotRequest timeSlotRequest) {
        TimeSlotResponse response = timeSlotService.createTimeSlot(productId, timeSlotRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/listTimeSlots")
    public PagedModel<TimeSlotResponse> listTimeSlots(@RequestParam Long productId,
                                                      @RequestParam LocalDateTime from,
                                                      @RequestParam LocalDateTime to,
                                                      @PageableDefault(size = 50) Pageable pageable) {
        return new PagedModel<>(timeSlotService.listTimeSlots(productId, from, to, pageable));
    }

    @PutMapping("/updateTimeSlot/{id}")
    public ResponseEntity<TimeSlotResponse> updateSlot(@PathVariable Long id, @Valid @RequestBody TimeSlotRequest timeSlotRequest) {
        TimeSlotResponse timeSlotResponse = timeSlotService.updateTimeSlot(id, timeSlotRequest);
        return ResponseEntity.ok(timeSlotResponse);
    }

    @PatchMapping("/cancelTimeSlot/{id}")
    public TimeSlotResponse cancelTimeSlot(@PathVariable Long id) {
        return timeSlotService.cancelTimeSlot(id);
    }

    @GetMapping("/getTimeSlot/{id}")
    public TimeSlotResponse getTimeSlotById(@PathVariable Long id) {
        return timeSlotService.getTimeSlotById(id);
    }

    @DeleteMapping("/deleteTimeSlot/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeSlotById(@PathVariable Long id) {
        timeSlotService.deleteTimeSlot(id);
    }
}