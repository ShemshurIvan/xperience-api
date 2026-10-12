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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    private static final String VALID_PRODUCT_JSON = """
            {
              "name": "  Drive a Lamborghini ",
              "description": "30 minutes on a race track",
              "price": 200.00,
              "maxPeople": 2,
              "durationMinutes": 30
            }
            """;

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

    // ---------- create ----------

    @Test
    void createProduct_returns201AndLinksOwner() throws Exception {
        User owner = saveUser("host@example.com");

        mockMvc.perform(post("/createProduct/" + owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Drive a Lamborghini"))
                .andExpect(jsonPath("$.price").value(200.00))
                .andExpect(jsonPath("$.maxPeople").value(2))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.ownerId").value(owner.getId()))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(productRepository.findAll())
                .singleElement()
                .satisfies(p -> assertThat(p.getOwner().getId()).isEqualTo(owner.getId()));
    }

    @Test
    void createProduct_returns404WhenOwnerMissing() throws Exception {
        mockMvc.perform(post("/createProduct/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PRODUCT_JSON))
                .andExpect(status().isNotFound());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    void createProduct_returns400WithFieldErrorsWhenInputInvalid() throws Exception {
        User owner = saveUser("host@example.com");
        String invalidJson = """
                {
                  "name": "",
                  "price": -5,
                  "maxPeople": 0,
                  "durationMinutes": 0
                }
                """;

        mockMvc.perform(post("/createProduct/" + owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.maxPeople").exists())
                .andExpect(jsonPath("$.errors.durationMinutes").exists());
    }

    // ---------- update ----------

    @Test
    void updateProduct_replacesAllFields() throws Exception {
        Product product = saveProduct(saveUser("host@example.com"));
        String json = """
                {
                  "name": "Drive a Ferrari",
                  "description": "Now in red",
                  "price": 350.00,
                  "maxPeople": 3,
                  "durationMinutes": 45
                }
                """;

        mockMvc.perform(put("/updateProduct/" + product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value("Drive a Ferrari"))
                .andExpect(jsonPath("$.price").value(350.00))
                .andExpect(jsonPath("$.maxPeople").value(3))
                .andExpect(jsonPath("$.durationMinutes").value(45));
    }

    @Test
    void updateProduct_returns404WhenMissing() throws Exception {
        mockMvc.perform(put("/updateProduct/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PRODUCT_JSON))
                .andExpect(status().isNotFound());
    }

    // ---------- read ----------

    @Test
    void getProduct_returnsProduct() throws Exception {
        Product product = saveProduct(saveUser("host@example.com"));

        mockMvc.perform(get("/getProduct/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value("Drive a Lamborghini"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    @Test
    void getProduct_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/getProduct/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllProducts_returnsRequestedPage() throws Exception {
        User owner = saveUser("host@example.com");
        saveProduct(owner);
        saveProduct(owner);
        saveProduct(owner);

        mockMvc.perform(get("/products").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    // ---------- delete ----------

    @Test
    void deleteProduct_returns204AndRemovesSlotsAndCartItems() throws Exception {
        User host = saveUser("host@example.com");
        User guest = saveUser("guest@example.com");
        Product product = saveProduct(host);
        TimeSlot slot = saveSlot(product, LocalDateTime.now().plusDays(3));
        saveCartItem(guest, slot);

        mockMvc.perform(delete("/deleteProduct/" + product.getId()))
                .andExpect(status().isNoContent());
        productRepository.flush();

        assertThat(productRepository.existsById(product.getId())).isFalse();
        assertThat(timeSlotRepository.existsById(slot.getId())).isFalse();
        assertThat(cartItemRepository.count()).isZero();
        assertThat(userRepository.existsById(host.getId())).isTrue();
    }

    @Test
    void deleteProduct_returns409WhenUpcomingBookingExists() throws Exception {
        Product product = saveProduct(saveUser("host@example.com"));
        TimeSlot slot = saveSlot(product, LocalDateTime.now().plusDays(3));
        saveReservation(slot, saveUser("guest@example.com"), ReservationStatus.CONFIRMED);

        mockMvc.perform(delete("/deleteProduct/" + product.getId()))
                .andExpect(status().isConflict());

        assertThat(productRepository.existsById(product.getId())).isTrue();
    }

    @Test
    void deleteProduct_succeedsWhenUpcomingBookingIsCancelled() throws Exception {
        Product product = saveProduct(saveUser("host@example.com"));
        TimeSlot slot = saveSlot(product, LocalDateTime.now().plusDays(3));
        saveReservation(slot, saveUser("guest@example.com"), ReservationStatus.CANCELLED);

        mockMvc.perform(delete("/deleteProduct/" + product.getId()))
                .andExpect(status().isNoContent());
        productRepository.flush();

        assertThat(productRepository.existsById(product.getId())).isFalse();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void deleteProduct_returns404WhenMissing() throws Exception {
        mockMvc.perform(delete("/deleteProduct/999999"))
                .andExpect(status().isNotFound());
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

    private Product saveProduct(User owner) {
        Product product = new Product();
        product.setName("Drive a Lamborghini");
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        owner.addProduct(product);
        return productRepository.save(product);
    }

    private TimeSlot saveSlot(Product product, LocalDateTime start) {
        TimeSlot slot = new TimeSlot();
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(product.getDurationMinutes()));
        slot.setMaxPeopleInSlot(4);
        product.addTimeSlot(slot);
        return timeSlotRepository.save(slot);
    }

    private void saveReservation(TimeSlot slot, User guest, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setTimeSlot(slot);
        reservation.setGuest(guest);
        reservation.setNumberPeople(2);
        reservation.setPricePaid(new BigDecimal("400.00"));
        reservation.setStatus(status);
        reservationRepository.save(reservation);
    }

    private void saveCartItem(User user, TimeSlot slot) {
        CartItem item = new CartItem();
        item.setUser(user);
        item.setTimeSlot(slot);
        item.setNumberPeople(1);
        cartItemRepository.save(item);
    }
}