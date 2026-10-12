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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserReadUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void getUserById_returnsUserWithoutPassword() throws Exception {
        User user = saveUser("ivan@example.com");

        mockMvc.perform(get("/get/" + user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/get/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_returnsRequestedPageWithTotals() throws Exception {
        saveUser("a@example.com");
        saveUser("b@example.com");
        saveUser("c@example.com");

        mockMvc.perform(get("/get").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("a@example.com"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void updateUser_changesFieldsAndRehashesPassword() throws Exception {
        User user = saveUser("ivan@example.com");

        String json = """
                {
                  "firstName": "  Ivan ",
                  "lastName": "Sidorov",
                  "email": "IVAN@example.com",
                  "password": "newSecret456",
                  "age": 31
                }
                """;

        mockMvc.perform(put("/updateUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.firstName").value("Ivan"))
                .andExpect(jsonPath("$.lastName").value("Sidorov"))
                .andExpect(jsonPath("$.age").value(31))
                .andExpect(jsonPath("$.password").doesNotExist());

        userRepository.flush();
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getLastName()).isEqualTo("Sidorov");
        assertThat(passwordEncoder.matches("newSecret456", updated.getPassword())).isTrue();
    }

    @Test
    void updateUser_returns404WhenEmailUnknown() throws Exception {
        String json = """
                {
                  "firstName": "Nobody",
                  "lastName": "Here",
                  "email": "nobody@example.com",
                  "password": "secret123",
                  "age": 30
                }
                """;

        mockMvc.perform(put("/updateUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAge(30);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("secret123"));
        return userRepository.save(user);
    }
}