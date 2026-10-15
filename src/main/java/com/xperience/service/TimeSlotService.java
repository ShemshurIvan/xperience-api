package com.xperience.service;

import com.xperience.dto.timeslot.TimeSlotRequest;
import com.xperience.dto.timeslot.TimeSlotResponse;
import com.xperience.entity.Product;
import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;
import com.xperience.exception.*;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.TimeSlotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final CartItemRepository cartItemRepository;

    public TimeSlotService(TimeSlotRepository timeSlotRepository, ProductRepository productRepository, ReservationRepository reservationRepository, CartItemRepository cartItemRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public TimeSlotResponse createTimeSlot(Long currentUserId, Long productId, TimeSlotRequest timeSlotRequest) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        checkOwner(product, currentUserId);

       LocalDateTime startTime = timeSlotRequest.startTime();
       LocalDateTime endTime = startTime.plusMinutes(product.getDurationMinutes());

       if(timeSlotRepository.existsOverlappingSlot(productId, startTime, endTime)) {
           throw new SlotOverlapException();
       }

       if(timeSlotRequest.maxPeopleInSlot() > product.getMaxPeople()) {
           throw new BadRequestException("The maximum number of people exceeded");
       }
       TimeSlot timeSlot = new TimeSlot();
       timeSlot.setStartTime(startTime);
       timeSlot.setEndTime(endTime);
       timeSlot.setMaxPeopleInSlot(timeSlotRequest.maxPeopleInSlot());
       product.addTimeSlot(timeSlot);

       return TimeSlotResponse.from(timeSlotRepository.saveAndFlush(timeSlot));
    }

    @Transactional(readOnly = true)
    public Page<TimeSlotResponse> listTimeSlots(Long productId,
                                                LocalDateTime from, LocalDateTime to,
                                                Pageable pageable) {

        if(!productRepository.existsById(productId)){
            throw new ProductNotFoundException(productId);
        }
        if(to.isBefore(from)) {
            throw new BadRequestException("from must be before to");
        }
        return timeSlotRepository.findByProductIdAndStartTimeBetweenOrderByStartTime(productId, from, to, pageable).map(TimeSlotResponse::from);
    }

    @Transactional
    public TimeSlotResponse updateTimeSlot(Long currentUserId, Long id, TimeSlotRequest timeSlotRequest) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new TimeSlotNotFoundException(id));

        Product product = slot.getProduct();
        checkOwner(product, currentUserId);

        if(slot.getStatus() == StatusSlot.CANCELLED){
            throw new BadRequestException("A cancelled slot can't be changed");
        }

        if(timeSlotRequest.maxPeopleInSlot() > product.getMaxPeople()) {
            throw new BadRequestException("The maximum number of people exceeded");
        }

        if(timeSlotRequest.maxPeopleInSlot() < slot.getBookedPeople()){
            throw new SlotConflictException("Capacity can't be lower than the " + slot.getBookedPeople() + " people already booked");
        }

        LocalDateTime newStartTime = timeSlotRequest.startTime();
        LocalDateTime newEndTime = newStartTime.plusMinutes(product.getDurationMinutes());
        boolean timeChanges = !newStartTime.equals(slot.getStartTime());

        if(timeChanges && slot.getBookedPeople() > 0) {
            throw new SlotConflictException("A slot with bookings can't be moved");
        }

        if(timeChanges && timeSlotRepository.existsOverlappingSlotExcluding(product.getId(), id, newStartTime, newEndTime)) {
            throw new SlotOverlapException();
        }

        slot.setStartTime(newStartTime);
        slot.setEndTime(newEndTime);
        slot.setMaxPeopleInSlot(timeSlotRequest.maxPeopleInSlot());

        slot.setStatus(slot.getPlacesLeft() > 0 ? StatusSlot.OPEN : StatusSlot.FULL);

        return TimeSlotResponse.from(slot);
    }

    @Transactional
    public TimeSlotResponse cancelTimeSlot(Long currentUserId, Long id) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new TimeSlotNotFoundException(id));
        checkOwner(slot.getProduct(), currentUserId);

        if (slot.getStatus() == StatusSlot.CANCELLED) {
            return TimeSlotResponse.from(slot);
        }
        if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("A slot that has already started can't be cancelled");
        }

        reservationRepository.cancelActiveByTimeSlotId(id);
        cartItemRepository.deleteByTimeSlotId(id);


        TimeSlot cancelled = timeSlotRepository.findById(id).orElseThrow();
        cancelled.setStatus(StatusSlot.CANCELLED);
        cancelled.setBookedPeople(0);

        return TimeSlotResponse.from(cancelled);
    }

    @Transactional(readOnly = true)
    public TimeSlotResponse getTimeSlotById(Long id) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new TimeSlotNotFoundException(id));
        return TimeSlotResponse.from(slot);
    }

    @Transactional
    public void deleteTimeSlot(Long currentUserId, Long id) {
        TimeSlot existing = timeSlotRepository.findById(id)
                .orElseThrow(() -> new TimeSlotNotFoundException(id));
        checkOwner(existing.getProduct(), currentUserId);

        if(reservationRepository.existsByTimeSlotId(id)) {
            throw new SlotConflictException("This slot has bookings and can't be deleted. Cancel it instead.");
        }

        cartItemRepository.deleteByTimeSlotId(id);

        TimeSlot slot = timeSlotRepository.findById(id).orElseThrow();
        slot.getProduct().removeTimeSlot(slot);
    }

    private void checkOwner(Product product, Long currentUserId) {
        if (!product.getOwner().getId().equals(currentUserId)) {
            throw new ForbiddenException("Only the owner of this experience can manage its time slots");
        }
    }
}
