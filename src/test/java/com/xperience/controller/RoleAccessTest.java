package com.xperience.controller;

import com.jayway.jsonpath.JsonPath;
import com.xperience.config.AdminAccountInitializer;
import com.xperience.entity.Role;
import com.xperience.entity.User;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role rules with real accounts and real tokens: register -> log in -> call endpoints.
 * USER buys, SELLER also sells, ADMIN can do everything (ADMIN > SELLER > USER).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.admin-email=boss@example.com")
class RoleAccessTest {

    private static final String PRODUCT_JSON = """
            {"name": "Drive a Lamborghini", "price": 200, "maxPeople": 4, "durationMinutes": 30}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AdminAccountInitializer adminAccountInitializer;

    private String buyerToken;
    private String sellerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        register("buyer@example.com", null);
        register("seller@example.com", "SELLER");
        register("boss@example.com", null);   // matches app.admin-email

        buyerToken = login("buyer@example.com");
        sellerToken = login("seller@example.com");
        adminToken = login("boss@example.com");
    }

    // ---------- registration ----------

    @Test
    void registration_givesUserByDefault_sellerOnRequest_adminOnlyForAdminEmail() {
        assertThat(roleOf("buyer@example.com")).isEqualTo(Role.USER);
        assertThat(roleOf("seller@example.com")).isEqualTo(Role.SELLER);
        assertThat(roleOf("boss@example.com")).isEqualTo(Role.ADMIN);
    }

    @Test
    void registration_returnsTheRole() throws Exception {
        registerRequest("new.seller@example.com", "SELLER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void registration_returns400WhenAskingForAdmin() throws Exception {
        registerRequest("hacker@example.com", "ADMIN")
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail("hacker@example.com")).isEmpty();
    }

    @Test
    void loginToken_containsTheRole() {
        assertThat(payloadOf(sellerToken)).contains("\"roles\":[\"SELLER\"]");
        assertThat(payloadOf(buyerToken)).contains("\"roles\":[\"USER\"]");
        assertThat(payloadOf(adminToken)).contains("\"roles\":[\"ADMIN\"]");
    }

    // ---------- selling: only sellers (and admins) ----------

    @Test
    void buyer_cannotCreateExperience() throws Exception {
        createProduct(buyerToken).andExpect(status().isForbidden());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    void seller_canCreateExperienceAndOpenSlots() throws Exception {
        Long productId = idOf(createProduct(sellerToken).andExpect(status().isCreated()));

        createSlot(sellerToken, productId).andExpect(status().isCreated());
    }

    @Test
    void admin_canCreateExperience() throws Exception {
        createProduct(adminToken).andExpect(status().isCreated());
    }

    @Test
    void buyer_cannotOpenSlotsEvenOnAnExistingExperience() throws Exception {
        Long productId = idOf(createProduct(sellerToken));

        createSlot(buyerToken, productId).andExpect(status().isForbidden());
    }

    // ---------- buying: everyone, sellers included ----------

    @Test
    void seller_canAlsoBuy() throws Exception {
        Long productId = idOf(createProduct(adminToken));
        Long slotId = idOf(createSlot(adminToken, productId));

        mockMvc.perform(post("/me/cart").header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeSlotId\": " + slotId + ", \"numberPeople\": 1}"))
                .andExpect(status().isCreated());
    }

    // ---------- admin powers ----------

    @Test
    void onlyAdmin_canListAllUsers() throws Exception {
        mockMvc.perform(get("/get").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/get").header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/get").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void admin_canDeleteButNotEditSomeoneElsesExperience() throws Exception {
        Long productId = idOf(createProduct(sellerToken));

        mockMvc.perform(put("/updateProduct/" + productId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/deleteProduct/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertThat(productRepository.existsById(productId)).isFalse();
    }

    @Test
    void otherSeller_cannotEditOrDeleteMyExperience() throws Exception {
        Long productId = idOf(createProduct(sellerToken));
        register("other.seller@example.com", "SELLER");
        String otherSellerToken = login("other.seller@example.com");

        mockMvc.perform(put("/updateProduct/" + productId).header("Authorization", "Bearer " + otherSellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/deleteProduct/" + productId).header("Authorization", "Bearer " + otherSellerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canDeleteSomeoneElsesAccount_userCannot() throws Exception {
        Long sellerId = userRepository.findByEmail("seller@example.com").orElseThrow().getId();
        Long adminId = userRepository.findByEmail("boss@example.com").orElseThrow().getId();

        mockMvc.perform(delete("/deleteUser/" + adminId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/deleteUser/" + sellerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertThat(userRepository.existsById(sellerId)).isFalse();
    }

    // ---------- admin set-up at startup ----------

    @Test
    void adminInitializer_promotesAnExistingAccountWithTheAdminEmail() throws Exception {
        User boss = userRepository.findByEmail("boss@example.com").orElseThrow();
        boss.setRole(Role.USER);   // as if they had registered before ADMIN_EMAIL was set

        adminAccountInitializer.run(null);

        assertThat(roleOf("boss@example.com")).isEqualTo(Role.ADMIN);
    }

    // ---------- helpers ----------

    private void register(String email, String accountType) throws Exception {
        registerRequest(email, accountType).andExpect(status().isCreated());
    }

    private ResultActions registerRequest(String email, String accountType) throws Exception {
        String type = accountType == null ? "" : ", \"accountType\": \"" + accountType + "\"";
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Test", "lastName": "User", "email": "%s", "password": "secret123", "age": 30%s}
                """.formatted(email, type)));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions createProduct(String token) throws Exception {
        return mockMvc.perform(post("/createProduct").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON));
    }

    private ResultActions createSlot(String token, Long productId) throws Exception {
        String start = LocalDateTime.now().plusDays(3).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return mockMvc.perform(post("/createTimeSlot/" + productId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\": \"" + start + "\", \"maxPeopleInSlot\": 2}"));
    }

    private Long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private Role roleOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getRole();
    }

    // the middle part of a JWT is base64url-encoded JSON
    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }
}