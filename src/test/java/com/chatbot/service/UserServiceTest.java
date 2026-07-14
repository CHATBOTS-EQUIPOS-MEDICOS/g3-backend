package com.chatbot.service;

import com.chatbot.controller.dto.UserRequest;
import com.chatbot.controller.dto.UserResponse;
import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Role clientRole;

    @BeforeEach
    void setUp() {
        clientRole = new Role(2L, NameRol.CLIENT);
    }

    @Test
    void getUserProfile_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setFullName("User Test");
        user.setId_rol(clientRole);
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        UserResponse response = userService.getUserProfile(userId);

        // Assert
        assertThat(response.getEmail()).isEqualTo("user@test.com");
        assertThat(response.getFullName()).isEqualTo("User Test");
        assertThat(response.getRole()).isEqualTo(NameRol.CLIENT.name());
    }

    @Test
    void getUserProfile_InactiveUser_ShouldThrowException() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setActive(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserProfile(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El usuario está desactivado.");
    }

    @Test
    void deactivateUser_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        userService.deactivateUser(userId);

        // Assert
        assertThat(user.getActive()).isFalse();
        assertThat(user.getFechaBaja()).isNotNull();
        assertThat(user.getEmail()).contains("disabled");
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_AlreadyInactive_ShouldThrowException() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setActive(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> userService.deactivateUser(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El usuario ya se encuentra desactivado.");
    }
}
