package com.xperience.service;

import com.xperience.dto.user.UserRequest;
import com.xperience.dto.user.UserResponse;
import com.xperience.entity.User;
import com.xperience.exception.conflict.EmailAlreadyExistsException;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private final UserRequest request =
            new UserRequest("  Ivan ", " Petrov ", "  Ivan@Example.COM ", "secret123", 25, null);

    @Test
    void createUser_savesNormalizedUserWithHashedPassword() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("Ivan");
        assertThat(saved.getLastName()).isEqualTo("Petrov");
        assertThat(saved.getEmail()).isEqualTo("ivan@example.com");
        assertThat(saved.getAge()).isEqualTo(25);
        assertThat(saved.getPassword()).isEqualTo("hashed-password");

        assertThat(response.firstName()).isEqualTo("Ivan");
        assertThat(response.lastName()).isEqualTo("Petrov");
        assertThat(response.email()).isEqualTo("ivan@example.com");
        assertThat(response.age()).isEqualTo(25);
    }

    @Test
    void createUser_throwsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ivan@example.com");

        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void createUser_translatesUniqueConstraintViolationToEmailAlreadyExists() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ivan@example.com");
    }
}