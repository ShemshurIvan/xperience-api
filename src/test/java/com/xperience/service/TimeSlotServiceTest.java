package com.xperience.service;

import com.xperience.dto.timeslot.TimeSlotRequest;
import com.xperience.dto.timeslot.TimeSlotResponse;
import com.xperience.entity.CartItem;
import com.xperience.entity.Product;
import com.xperience.entity.Reservation;
import com.xperience.entity.ReservationStatus;
import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import com.xperience.exception.BadRequestException;
import com.xperience.exception.ProductNotFoundException;
import com.xperience.exception.SlotOverlapException;
import com.xperience.exception.TimeSlotNotFoundException;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.TimeSlotRepository;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TimeSlotServiceTest {

    // a fixed day in the future keeps the times easy to read
    private static final LocalDateTime DAY = LocalDateTime.now().plusDays(10).withHour(0).withMinute(0).withSecond(0).withNano(0);

    @Autowired
    private TimeSlotService timeSlotService;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Product product;
    private Long hostId;   // the product owner, who is allowed to manage its slots

    @BeforeEach
    void setUp() {
        User host = new User();
        host.setFirstName("Host");
        host.setLastName("User");
        host.setAge(30);
        host.setEmail("host@example.com");
        host.setPassword("hashed-password");
        userRepository.save(host);
        hostId = host.getId();

        product = new Product();
        product.setName("Drive a Lamborghini");
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        host.addProduct(product);
        productRepository.save(product);
    }

    // ---------- createTimeSlot ----------

    @Test
    void createTimeSlot_calculatesEndTimeFromProductDuration() {
        TimeSlotResponse response = timeSlotService.createTimeSlot(hostId, 
                product.getId(), new TimeSlotRequest(at(10, 0), 3));

        assertThat(response.id()).isNotNull();
        assertThat(response.productId()).isEqualTo(product.getId());
        assertThat(response.startTime()).isEqualTo(at(10, 0));
        assertThat(response.endTime()).isEqualTo(at(11, 0));
        assertThat(response.maxPeopleInSlot()).isEqualTo(3);
        assertThat(response.placesLeft()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(StatusSlot.OPEN);
    }

    @Test
    void createTimeSlot_throwsWhenProductMissing() {
        assertThatThrownBy(() -> timeSlotService.createTimeSlot(hostId, 999_999L, new TimeSlotRequest(at(10, 0), 2)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void createTimeSlot_throwsWhenMorePeopleThanProductAllows() {
        assertThatThrownBy(() -> timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 5)))
                .isInstanceOf(BadRequestException.class);

        assertThat(timeSlotRepository.count()).isZero();
    }

    @Test
    void createTimeSlot_throwsWhenOverlappingAnotherSlot() {
        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 2));   // 10:00–11:00

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 30), 2)))
                .isInstanceOf(SlotOverlapException.class);
        assertThatThrownBy(() -> timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(9, 30), 2)))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    void createTimeSlot_allowsBackToBackSlots() {
        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 2));   // 10:00–11:00

        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(11, 0), 2));   // 11:00–12:00
        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(9, 0), 2));    // 09:00–10:00

        assertThat(timeSlotRepository.count()).isEqualTo(3);
    }

    @Test
    void createTimeSlot_ignoresCancelledSlotsWhenCheckingOverlap() {
        TimeSlotResponse first = timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 2));
        TimeSlot cancelled = timeSlotRepository.findById(first.id()).orElseThrow();
        cancelled.setStatus(StatusSlot.CANCELLED);

        TimeSlotResponse replacement = timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 30), 2));

        assertThat(replacement.id()).isNotNull();
    }

    // ---------- calendar query ----------

    @Test
    void calendarQuery_returnsOnlySlotsInRangeSortedByStartTime() {
        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(14, 0), 2));
        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 2));
        timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(DAY.plusDays(5).withHour(10), 2)); // out of range

        Page<TimeSlot> page = timeSlotRepository.findByProductIdAndStartTimeBetweenOrderByStartTime(
                product.getId(), DAY, DAY.plusDays(1), PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(TimeSlot::getStartTime)
                .containsExactly(at(10, 0), at(14, 0));
    }

    // ---------- cancelTimeSlot ----------

    @Test
    void cancelTimeSlot_cancelsSlotAndItsBookingsAndRemovesItFromCarts() {
        TimeSlotResponse created = timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 4));
        TimeSlot slot = timeSlotRepository.findById(created.id()).orElseThrow();
        slot.setBookedPeople(3);
        User guest = saveGuest();
        Reservation confirmed = saveReservation(slot, guest, ReservationStatus.CONFIRMED);
        Reservation pending = saveReservation(slot, guest, ReservationStatus.PENDING);
        saveCartItem(guest, slot);

        TimeSlotResponse response = timeSlotService.cancelTimeSlot(hostId, slot.getId());

        assertThat(response.status()).isEqualTo(StatusSlot.CANCELLED);
        assertThat(response.placesLeft()).isEqualTo(4);
        assertThat(reservationRepository.findById(confirmed.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservationRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cartItemRepository.count()).isZero();
        assertThat(timeSlotRepository.existsById(slot.getId())).isTrue();
    }

    @Test
    void cancelTimeSlot_isHarmlessWhenAlreadyCancelled() {
        TimeSlotResponse created = timeSlotService.createTimeSlot(hostId, product.getId(), new TimeSlotRequest(at(10, 0), 2));
        timeSlotService.cancelTimeSlot(hostId, created.id());

        TimeSlotResponse again = timeSlotService.cancelTimeSlot(hostId, created.id());

        assertThat(again.status()).isEqualTo(StatusSlot.CANCELLED);
    }

    @Test
    void cancelTimeSlot_throwsWhenSlotAlreadyStarted() {
        TimeSlot past = new TimeSlot();
        past.setStartTime(LocalDateTime.now().minusHours(2));
        past.setEndTime(LocalDateTime.now().minusHours(1));
        past.setMaxPeopleInSlot(2);
        product.addTimeSlot(past);
        timeSlotRepository.save(past);

        assertThatThrownBy(() -> timeSlotService.cancelTimeSlot(hostId, past.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelTimeSlot_throwsWhenSlotMissing() {
        assertThatThrownBy(() -> timeSlotService.cancelTimeSlot(hostId, 999_999L))
                .isInstanceOf(TimeSlotNotFoundException.class);
    }

    private User saveGuest() {
        User guest = new User();
        guest.setFirstName("Guest");
        guest.setLastName("User");
        guest.setAge(25);
        guest.setEmail("guest@example.com");
        guest.setPassword("hashed-password");
        return userRepository.save(guest);
    }

    private Reservation saveReservation(TimeSlot slot, User guest, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setTimeSlot(slot);
        reservation.setGuest(guest);
        reservation.setNumberPeople(1);
        reservation.setPricePaid(new BigDecimal("200.00"));
        reservation.setStatus(status);
        return reservationRepository.save(reservation);
    }

    private void saveCartItem(User user, TimeSlot slot) {
        CartItem item = new CartItem();
        item.setUser(user);
        item.setTimeSlot(slot);
        item.setNumberPeople(1);
        cartItemRepository.save(item);
    }

    private static LocalDateTime at(int hour, int minute) {
        return DAY.withHour(hour).withMinute(minute);
    }
}