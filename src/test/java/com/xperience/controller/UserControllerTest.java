package com.xperience.controller;

import com.xperience.entity.User;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    // Change this when the endpoint moves to POST /api/users
    private static final String CREATE_USER_URL = "/createUser";

    private static final String VALID_USER_JSON = """
            {
              "firstName": "Ivan",
              "lastName": "Petrov",
              "email": "Ivan@Example.com",
              "password": "secret123",
              "age": 25
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createUser_returns201WithUserAndNoPassword() throws Exception {
        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_USER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Ivan"))
                .andExpect(jsonPath("$.lastName").value("Petrov"))
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                .andExpect(jsonPath("$.age").value(25))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createUser_storesHashedPassword() throws Exception {
        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_USER_JSON))
                .andExpect(status().isCreated());

        User stored = userRepository.findAll().getFirst();
        assertThat(stored.getPassword()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", stored.getPassword())).isTrue();
    }

    @Test
    void createUser_returns409WhenEmailAlreadyExists() throws Exception {
        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_USER_JSON))
                .andExpect(status().isCreated());

        String sameEmailDifferentCase = VALID_USER_JSON.replace("Ivan@Example.com", "IVAN@example.COM");

        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sameEmailDifferentCase))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User with email ivan@example.com already exists"));
    }

    @Test
    void createUser_returns400WithFieldErrorsWhenInputInvalid() throws Exception {
        String invalidJson = """
                {
                  "firstName": "",
                  "lastName": "Petrov",
                  "email": "not-an-email",
                  "password": "short",
                  "age": 12
                }
                """;

        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.age").exists());

        assertThat(userRepository.count()).isZero();
    }
}