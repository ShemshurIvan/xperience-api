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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

        mockMvc.perform(get("/api/users/" + user.getId()).with(loggedInAs(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/users/999999").with(loggedInAs(1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_returnsRequestedPageWithTotals() throws Exception {
        User a = saveUser("a@example.com");
        saveUser("b@example.com");
        saveUser("c@example.com");

        mockMvc.perform(get("/api/users").param("page", "0").param("size", "2")
                        .with(jwt().jwt(token -> token.subject(a.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
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

        mockMvc.perform(put("/api/me/update")
                        .with(loggedInAs(user.getId()))
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
    void updateUser_canChangeEmailToAFreeOne() throws Exception {
        User user = saveUser("ivan@example.com");

        mockMvc.perform(put("/api/me/update")
                        .with(loggedInAs(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("New.Ivan@Example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.ivan@example.com"));
    }

    @Test
    void updateUser_returns409WhenEmailBelongsToAnotherUser() throws Exception {
        User user = saveUser("ivan@example.com");
        saveUser("taken@example.com");

        mockMvc.perform(put("/api/me/update")
                        .with(loggedInAs(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("taken@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUser_returns401WithoutToken() throws Exception {
        saveUser("ivan@example.com");

        mockMvc.perform(put("/api/me/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("ivan@example.com")))
                .andExpect(status().isUnauthorized());
    }

    private static String userJson(String email) {
        return """
                {"firstName": "Ivan", "lastName": "Petrov", "email": "%s", "password": "secret123", "age": 30}
                """.formatted(email);
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
        user.setPassword(passwordEncoder.encode("secret123"));
        return userRepository.save(user);
    }
}