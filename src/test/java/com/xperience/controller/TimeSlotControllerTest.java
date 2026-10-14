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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimeSlotControllerTest {

    private static final DateTimeFormatter JSON_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // a day in the future, so @Future validation passes
    private static final LocalDateTime DAY = LocalDateTime.now().plusDays(10)
            .withHour(0).withMinute(0).withSecond(0).withNano(0);

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
    private Product product;   // 60 minutes, up to 4 people

    @BeforeEach
    void setUp() {
        host = saveUser("host@example.com");
        product = new Product();
        product.setName("Drive a Lamborghini");
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        host.addProduct(product);
        productRepository.save(product);
    }

    // ---------- POST /createTimeSlot/{productId} ----------

    @Test
    void createTimeSlot_returns201AndCalculatesEndTime() throws Exception {
        createSlot(product.getId(), at(10, 0), 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.startTime").value(json(at(10, 0))))
                .andExpect(jsonPath("$.endTime").value(json(at(11, 0))))
                .andExpect(jsonPath("$.maxPeopleInSlot").value(3))
                .andExpect(jsonPath("$.placesLeft").value(3))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createTimeSlot_returns404WhenProductMissing() throws Exception {
        createSlot(999_999L, at(10, 0), 2)
                .andExpect(status().isNotFound());
    }

    @Test
    void createTimeSlot_returns400ForPastStartAndZeroPeople() throws Exception {
        createSlot(product.getId(), LocalDateTime.now().minusDays(1), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startTime").exists())
                .andExpect(jsonPath("$.errors.maxPeopleInSlot").exists());
    }

    @Test
    void createTimeSlot_returns400WhenMorePeopleThanProductAllows() throws Exception {
        createSlot(product.getId(), at(10, 0), 5)
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTimeSlot_returns409WhenOverlapping() throws Exception {
        createSlot(product.getId(), at(10, 0), 2).andExpect(status().isCreated());

        createSlot(product.getId(), at(10, 30), 2)
                .andExpect(status().isConflict());
    }

    // ---------- GET /listTimeSlots ----------

    @Test
    void listTimeSlots_returnsOnlySlotsInRangeSortedByStart() throws Exception {
        saveSlot(at(14, 0), 2);
        saveSlot(at(10, 0), 2);
        saveSlot(DAY.plusDays(3).withHour(10), 2);   // outside the range

        mockMvc.perform(get("/listTimeSlots")
                        .param("productId", product.getId().toString())
                        .param("from", json(DAY))
                        .param("to", json(DAY.plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].startTime").value(json(at(10, 0))))
                .andExpect(jsonPath("$.content[1].startTime").value(json(at(14, 0))))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void listTimeSlots_returns400WhenRangeIsReversed() throws Exception {
        mockMvc.perform(get("/listTimeSlots")
                        .param("productId", product.getId().toString())
                        .param("from", json(DAY.plusDays(1)))
                        .param("to", json(DAY)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTimeSlots_returns404WhenProductMissing() throws Exception {
        mockMvc.perform(get("/listTimeSlots")
                        .param("productId", "999999")
                        .param("from", json(DAY))
                        .param("to", json(DAY.plusDays(1))))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /getTimeSlot/{id} ----------

    @Test
    void getTimeSlot_returnsSlot() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);

        mockMvc.perform(get("/getTimeSlot/" + slot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(slot.getId()))
                .andExpect(jsonPath("$.startTime").value(json(at(10, 0))));
    }

    @Test
    void getTimeSlot_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/getTimeSlot/999999"))
                .andExpect(status().isNotFound());
    }

    // ---------- PUT /updateTimeSlot/{id} ----------

    @Test
    void updateTimeSlot_movesUnbookedSlotAndRecalculatesEndTime() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);

        updateSlot(slot.getId(), at(15, 0), 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value(json(at(15, 0))))
                .andExpect(jsonPath("$.endTime").value(json(at(16, 0))))
                .andExpect(jsonPath("$.maxPeopleInSlot").value(3));
    }

    @Test
    void updateTimeSlot_allowsMovingIntoItsOwnOldTime() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);

        updateSlot(slot.getId(), at(10, 30), 2)   // overlaps only its own old 10:00–11:00
                .andExpect(status().isOk());
    }

    @Test
    void updateTimeSlot_returns409WhenOverlappingAnotherSlot() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);
        saveSlot(at(12, 0), 2);

        updateSlot(slot.getId(), at(11, 30), 2)
                .andExpect(status().isConflict());
    }

    @Test
    void updateTimeSlot_returns409WhenMovingABookedSlot() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 4);
        slot.setBookedPeople(1);

        updateSlot(slot.getId(), at(15, 0), 4)
                .andExpect(status().isConflict());
    }

    @Test
    void updateTimeSlot_returns409WhenCapacityBelowBookedPeople() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 4);
        slot.setBookedPeople(3);

        updateSlot(slot.getId(), at(10, 0), 2)
                .andExpect(status().isConflict());
    }

    @Test
    void updateTimeSlot_reopensFullSlotWhenCapacityIsRaised() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);
        slot.setBookedPeople(2);
        slot.setStatus(StatusSlot.FULL);

        updateSlot(slot.getId(), at(10, 0), 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placesLeft").value(2))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void updateTimeSlot_returns400WhenSlotIsCancelled() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);
        slot.setStatus(StatusSlot.CANCELLED);

        updateSlot(slot.getId(), at(10, 0), 3)
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimeSlot_returns404WhenMissing() throws Exception {
        updateSlot(999_999L, at(10, 0), 2)
                .andExpect(status().isNotFound());
    }

    // ---------- PATCH /cancelTimeSlot/{id} ----------

    @Test
    void cancelTimeSlot_returns200AndCancelsBookings() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 4);
        Reservation booking = saveReservation(slot, saveUser("guest@example.com"));

        mockMvc.perform(patch("/cancelTimeSlot/" + slot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reservationRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelTimeSlot_returns404WhenMissing() throws Exception {
        mockMvc.perform(patch("/cancelTimeSlot/999999"))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /deleteTimeSlot/{id} ----------

    @Test
    void deleteTimeSlot_returns204AndRemovesSlotAndCartItems() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);
        saveCartItem(saveUser("guest@example.com"), slot);

        mockMvc.perform(delete("/deleteTimeSlot/" + slot.getId()))
                .andExpect(status().isNoContent());
        timeSlotRepository.flush();

        assertThat(timeSlotRepository.existsById(slot.getId())).isFalse();
        assertThat(cartItemRepository.count()).isZero();
        assertThat(productRepository.existsById(product.getId())).isTrue();
    }

    @Test
    void deleteTimeSlot_returns409WhenSlotHasBookings() throws Exception {
        TimeSlot slot = saveSlot(at(10, 0), 2);
        saveReservation(slot, saveUser("guest@example.com"));

        mockMvc.perform(delete("/deleteTimeSlot/" + slot.getId()))
                .andExpect(status().isConflict());

        assertThat(timeSlotRepository.existsById(slot.getId())).isTrue();
    }

    @Test
    void deleteTimeSlot_returns404WhenMissing() throws Exception {
        mockMvc.perform(delete("/deleteTimeSlot/999999"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private ResultActions createSlot(Long productId, LocalDateTime start, int maxPeople) throws Exception {
        return mockMvc.perform(post("/createTimeSlot/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotJson(start, maxPeople)));
    }

    private ResultActions updateSlot(Long slotId, LocalDateTime start, int maxPeople) throws Exception {
        return mockMvc.perform(put("/updateTimeSlot/" + slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotJson(start, maxPeople)));
    }

    private static String slotJson(LocalDateTime start, int maxPeople) {
        return """
                {"startTime": "%s", "maxPeopleInSlot": %d}
                """.formatted(json(start), maxPeople);
    }

    private static String json(LocalDateTime time) {
        return time.format(JSON_TIME);
    }

    private static LocalDateTime at(int hour, int minute) {
        return DAY.withHour(hour).withMinute(minute);
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

    private TimeSlot saveSlot(LocalDateTime start, int maxPeople) {
        TimeSlot slot = new TimeSlot();
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(product.getDurationMinutes()));
        slot.setMaxPeopleInSlot(maxPeople);
        product.addTimeSlot(slot);
        return timeSlotRepository.save(slot);
    }

    private Reservation saveReservation(TimeSlot slot, User guest) {
        Reservation reservation = new Reservation();
        reservation.setTimeSlot(slot);
        reservation.setGuest(guest);
        reservation.setNumberPeople(1);
        reservation.setPricePaid(new BigDecimal("200.00"));
        reservation.setStatus(ReservationStatus.CONFIRMED);
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