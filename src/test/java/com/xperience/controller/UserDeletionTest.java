package com.xperience.controller;

import com.xperience.entity.CartItem;
import com.xperience.entity.Product;
import com.xperience.entity.Reservation;
import com.xperience.entity.ReservationStatus;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.TimeSlotRepository;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserDeletionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void deleteUser_removesProductsSlotsAndCart_butKeepsTheirBookings() throws Exception {
        User host = saveUser("host@example.com");
        User guest = saveUser("guest@example.com");

        // the guest's own experience, with a slot nobody booked
        TimeSlot guestSlot = saveSlot(saveProduct(guest), LocalDateTime.now().plusDays(3));

        // the guest booked the host's experience and has another slot in the cart
        TimeSlot hostSlot = saveSlot(saveProduct(host), LocalDateTime.now().plusDays(5));
        Reservation booking = saveReservation(hostSlot, guest, ReservationStatus.CONFIRMED);
        saveCartItem(guest, hostSlot);

        mockMvc.perform(delete("/deleteUser/" + guest.getId()))
                .andExpect(status().isNoContent());
        userRepository.flush();

        assertThat(userRepository.findById(guest.getId())).isEmpty();
        assertThat(timeSlotRepository.findById(guestSlot.getId())).isEmpty();
        assertThat(productRepository.findAll()).extracting(p -> p.getOwner().getId())
                .containsOnly(host.getId());
        assertThat(cartItemRepository.count()).isZero();

        Reservation kept = reservationRepository.findById(booking.getId()).orElseThrow();
        assertThat(kept.getGuest()).isNull();
        assertThat(kept.getTimeSlot().getId()).isEqualTo(hostSlot.getId());
        assertThat(kept.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void deleteHost_returns409WhenOtherGuestsHaveUpcomingBookings() throws Exception {
        User host = saveUser("host@example.com");
        User guest = saveUser("guest@example.com");
        TimeSlot slot = saveSlot(saveProduct(host), LocalDateTime.now().plusDays(5));
        saveReservation(slot, guest, ReservationStatus.PENDING);

        mockMvc.perform(delete("/deleteUser/" + host.getId()))
                .andExpect(status().isConflict());

        assertThat(userRepository.existsById(host.getId())).isTrue();
        assertThat(timeSlotRepository.existsById(slot.getId())).isTrue();
    }

    @Test
    void deleteHost_succeedsWhenOtherGuestsBookingsAreCancelledOrPast() throws Exception {
        User host = saveUser("host@example.com");
        User guest = saveUser("guest@example.com");
        Product product = saveProduct(host);
        TimeSlot futureSlot = saveSlot(product, LocalDateTime.now().plusDays(5));
        TimeSlot pastSlot = saveSlot(product, LocalDateTime.now().minusDays(5));
        saveReservation(futureSlot, guest, ReservationStatus.CANCELLED);
        saveReservation(pastSlot, guest, ReservationStatus.CONFIRMED);
        saveCartItem(guest, futureSlot);

        mockMvc.perform(delete("/deleteUser/" + host.getId()))
                .andExpect(status().isNoContent());
        userRepository.flush();

        assertThat(userRepository.existsById(host.getId())).isFalse();
        assertThat(productRepository.count()).isZero();
        assertThat(timeSlotRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isZero();
        assertThat(userRepository.existsById(guest.getId())).isTrue();
    }

    @Test
    void deleteUser_returns404WhenUserDoesNotExist() throws Exception {
        mockMvc.perform(delete("/deleteUser/999999"))
                .andExpect(status().isNotFound());
    }

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAge(30);
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userRepository.save(user);
    }

    private Product saveProduct(User owner) {
        Product product = new Product();
        product.setName("Drive a Lamborghini");
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        product.setOwner(owner);
        return productRepository.save(product);
    }

    private TimeSlot saveSlot(Product product, LocalDateTime start) {
        TimeSlot slot = new TimeSlot();
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(1));
        slot.setMaxPeopleInSlot(4);
        product.addTimeSlot(slot);
        return timeSlotRepository.save(slot);
    }

    private Reservation saveReservation(TimeSlot slot, User guest, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setTimeSlot(slot);
        reservation.setGuest(guest);
        reservation.setNumberPeople(2);
        reservation.setPricePaid(new BigDecimal("400.00"));
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
}