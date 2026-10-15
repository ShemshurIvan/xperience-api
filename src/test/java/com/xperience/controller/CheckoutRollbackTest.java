package com.xperience.controller;

import com.xperience.entity.CartItem;
import com.xperience.entity.Product;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.TimeSlotRepository;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional on purpose: every call commits for real, so we can check that a failed
 * checkout really rolls back the places it had already taken. Data is deleted after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutRollbackTest {

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

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll();
        cartItemRepository.deleteAll();
        timeSlotRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void checkout_rollsBackEverythingWhenOneLineFails() throws Exception {
        User host = saveUser("host@example.com");
        User guest = saveUser("guest@example.com");
        Product product = saveProduct(host);

        // added first, so checkout processes it first and takes its places
        TimeSlot okSlot = saveSlot(product, LocalDateTime.now().plusDays(3), 4, 0);
        saveCartItem(guest, okSlot, 2);

        // added second: only 1 place left, but the guest wants 3
        TimeSlot fullishSlot = saveSlot(product, LocalDateTime.now().plusDays(4), 4, 3);
        saveCartItem(guest, fullishSlot, 3);

        mockMvc.perform(post("/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isConflict());

        // the first line's places were given back, nothing was booked, and the cart is untouched
        assertThat(timeSlotRepository.findById(okSlot.getId()).orElseThrow().getBookedPeople()).isZero();
        assertThat(timeSlotRepository.findById(fullishSlot.getId()).orElseThrow().getBookedPeople()).isEqualTo(3);
        assertThat(reservationRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isEqualTo(2);
    }

    // a request with a valid token for this user, as if they had logged in
    private static RequestPostProcessor loggedInAs(Long userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
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

    private TimeSlot saveSlot(Product product, LocalDateTime start, int maxPeople, int booked) {
        TimeSlot slot = new TimeSlot();
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(product.getDurationMinutes()));
        slot.setMaxPeopleInSlot(maxPeople);
        slot.setBookedPeople(booked);
        slot.setProduct(product);
        return timeSlotRepository.save(slot);
    }

    private void saveCartItem(User user, TimeSlot slot, int people) throws InterruptedException {
        CartItem item = new CartItem();
        item.setUser(user);
        item.setTimeSlot(slot);
        item.setNumberPeople(people);
        cartItemRepository.save(item);
        Thread.sleep(5);   // distinct addedAt, so the cart order (and checkout order) is predictable
    }
}