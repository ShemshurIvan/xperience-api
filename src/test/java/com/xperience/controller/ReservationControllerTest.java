package com.xperience.controller;

import com.xperience.entity.CartItem;
import com.xperience.entity.Product;
import com.xperience.entity.Reservation;
import com.xperience.entity.ReservationStatus;
import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.TimeSlotRepository;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationControllerTest {

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

    private User host;
    private User guest;
    private Product product;   // 200.00 per person, up to 4 people

    @BeforeEach
    void setUp() {
        host = saveUser("host@example.com");
        guest = saveUser("guest@example.com");

        product = new Product();
        product.setName("Drive a Lamborghini");
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        host.addProduct(product);
        productRepository.save(product);
    }

    // ---------- POST /users/{userId}/checkout ----------

    @Test
    void checkout_returns201AndCreatesConfirmedReservations() throws Exception {
        TimeSlot first = saveSlot(LocalDateTime.now().plusDays(3), 4);
        TimeSlot second = saveSlot(LocalDateTime.now().plusDays(4), 4);
        saveCartItem(guest, first, 2);
        saveCartItem(guest, second, 1);

        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].guestId").value(guest.getId()))
                .andExpect(jsonPath("$[0].productId").value(product.getId()));

        assertThat(reservationRepository.count()).isEqualTo(2);
    }

    @Test
    void checkout_takesPlacesFixesPriceAndClearsCart() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        saveCartItem(guest, slot, 3);

        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].numberPeople").value(3))
                .andExpect(jsonPath("$[0].pricePaid").value(600.00))
                .andExpect(jsonPath("$[0].timeSlotId").value(slot.getId()));

        TimeSlot updated = timeSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(updated.getBookedPeople()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(StatusSlot.OPEN);
        assertThat(cartItemRepository.count()).isZero();
    }

    @Test
    void checkout_marksSlotFullWhenLastPlacesAreTaken() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 2);
        saveCartItem(guest, slot, 2);

        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isCreated());

        TimeSlot updated = timeSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(updated.getPlacesLeft()).isZero();
        assertThat(updated.getStatus()).isEqualTo(StatusSlot.FULL);
    }

    @Test
    void checkout_returns400WhenCartIsEmpty() throws Exception {
        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cart is empty"));
    }

    @Test
    void checkout_returns409WhenNotEnoughPlacesLeft() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        saveCartItem(guest, slot, 3);
        slot.setBookedPeople(2);   // someone else booked in the meantime

        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("only 2 places left")));
    }

    @Test
    void checkout_returns409WhenSlotWasCancelled() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        saveCartItem(guest, slot, 1);
        slot.setStatus(StatusSlot.CANCELLED);

        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void checkout_returns409WhenSlotAlreadyStarted() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().minusHours(1), 4);
        saveCartItem(guest, slot, 1);

        mockMvc.perform(post("/api/me/checkout").with(loggedInAs(guest.getId())))
                .andExpect(status().isConflict());
    }

    // ---------- GET /users/{userId}/reservations ----------

    @Test
    void getMyReservations_returnsOnlyThatGuestsBookingsNewestFirst() throws Exception {
        TimeSlot earlier = saveSlot(LocalDateTime.now().plusDays(3), 4);
        TimeSlot later = saveSlot(LocalDateTime.now().plusDays(5), 4);
        saveReservation(earlier, guest, ReservationStatus.CONFIRMED);
        saveReservation(later, guest, ReservationStatus.CONFIRMED);
        saveReservation(later, saveUser("other@example.com"), ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/me/reservations").with(loggedInAs(guest.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].timeSlotId").value(later.getId()))
                .andExpect(jsonPath("$.content[1].timeSlotId").value(earlier.getId()))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void getMyReservations_returns404WhenTokenUserNoLongerExists() throws Exception {
        mockMvc.perform(get("/api/me/reservations").with(loggedInAs(999_999L)))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /getReservation/{id} ----------

    @Test
    void getReservation_returnsReservation() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/reservations/" + reservation.getId()).with(loggedInAs(guest.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservation.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getReservation_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/reservations/999999").with(loggedInAs(guest.getId())))
                .andExpect(status().isNotFound());
    }

    // ---------- PATCH /cancelReservation/{id} ----------

    @Test
    void cancelReservation_givesPlacesBackAndReopensFullSlot() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 2);
        slot.setBookedPeople(2);
        slot.setStatus(StatusSlot.FULL);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CONFIRMED);   // 2 people

        mockMvc.perform(patch("/api/reservations/cancel/" + reservation.getId()).with(loggedInAs(guest.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        TimeSlot updated = timeSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(updated.getBookedPeople()).isZero();
        assertThat(updated.getStatus()).isEqualTo(StatusSlot.OPEN);
    }

    @Test
    void cancelReservation_isHarmlessWhenAlreadyCancelled() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        slot.setBookedPeople(1);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CANCELLED);

        mockMvc.perform(patch("/api/reservations/cancel/" + reservation.getId()).with(loggedInAs(guest.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(timeSlotRepository.findById(slot.getId()).orElseThrow().getBookedPeople()).isEqualTo(1);
    }

    @Test
    void cancelReservation_returns400WhenSlotAlreadyStarted() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().minusHours(1), 4);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CONFIRMED);

        mockMvc.perform(patch("/api/reservations/cancel/" + reservation.getId()).with(loggedInAs(guest.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelReservation_returns404WhenMissing() throws Exception {
        mockMvc.perform(patch("/api/reservations/cancel/999999").with(loggedInAs(guest.getId())))
                .andExpect(status().isNotFound());
    }

    // ---------- security ----------

    @Test
    void checkout_returns401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/me/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelReservation_returns403ForSomeoneElsesBooking() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CONFIRMED);
        User stranger = saveUser("stranger@example.com");

        mockMvc.perform(patch("/api/reservations/cancel/" + reservation.getId()).with(loggedInAs(stranger.getId())))
                .andExpect(status().isForbidden());

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void getReservation_hostCanSeeBookingsOfTheirExperience() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/reservations/" + reservation.getId()).with(loggedInAs(host.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void getReservation_returns403ForStrangers() throws Exception {
        TimeSlot slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
        Reservation reservation = saveReservation(slot, guest, ReservationStatus.CONFIRMED);
        User stranger = saveUser("stranger@example.com");

        mockMvc.perform(get("/api/reservations/" + reservation.getId()).with(loggedInAs(stranger.getId())))
                .andExpect(status().isForbidden());
    }

    // a request with a valid token for this user, as if they had logged in
    private static RequestPostProcessor loggedInAs(Long userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    // ---------- helpers ----------

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAge(30);
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userRepository.save(user);
    }

    private TimeSlot saveSlot(LocalDateTime start, int maxPeople) {
        TimeSlot slot = new TimeSlot();
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(product.getDurationMinutes()));
        slot.setMaxPeopleInSlot(maxPeople);
        product.addTimeSlot(slot);
        return timeSlotRepository.save(slot);
    }

    private void saveCartItem(User user, TimeSlot slot, int people) {
        CartItem item = new CartItem();
        item.setUser(user);
        item.setTimeSlot(slot);
        item.setNumberPeople(people);
        cartItemRepository.save(item);
    }

    private Reservation saveReservation(TimeSlot slot, User user, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setTimeSlot(slot);
        reservation.setGuest(user);
        reservation.setNumberPeople(2);
        reservation.setPricePaid(new BigDecimal("400.00"));
        reservation.setStatus(status);
        return reservationRepository.save(reservation);
    }
}