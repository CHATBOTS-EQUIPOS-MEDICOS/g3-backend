package com.chatbot.service;

import com.chatbot.controller.dto.AdminUserCreateRequest;
import com.chatbot.controller.dto.AdminUserResponse;
import com.chatbot.controller.dto.AdminUserUpdateRequest;
import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Role adminRole;
    private Role clientRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role(1L, NameRol.ADMIN);
        clientRole = new Role(2L, NameRol.CLIENT);
    }

    @Test
    void getAllUsers_ShouldReturnSortedUsers() {
        // Arrange
        User u1 = new User();
        u1.setCreatedAt(LocalDateTime.now().minusDays(1));
        User u2 = new User();
        u2.setCreatedAt(LocalDateTime.now());

        when(userRepository.findAll()).thenReturn(Arrays.asList(u1, u2));

        // Act
        List<AdminUserResponse> result = userService.getAllUsers();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCreatedAt()).isEqualTo(u2.getCreatedAt()); // u2 is newer, should be first
    }

    @Test
    void adminCreateUser_WhenEmailAlreadyExists_ShouldThrowException() {
        // Arrange
        AdminUserCreateRequest request = new AdminUserCreateRequest("Test", "test@test.com", "pass123", NameRol.CLIENT);
        when(userRepository.findFirstByEmail(any())).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThatThrownBy(() -> userService.adminCreateUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El correo ya está registrado.");
    }

    @Test
    void adminCreateUser_Success() {
        // Arrange
        AdminUserCreateRequest request = new AdminUserCreateRequest("Test User", "test@test.com", "pass123", NameRol.CLIENT);
        User savedUser = new User();
        savedUser.setFullName("Test User");
        savedUser.setEmail("test@test.com");
        savedUser.setId_rol(clientRole);
        savedUser.setActive(true);

        when(userRepository.findFirstByEmail(any())).thenReturn(Optional.empty());
        when(roleRepository.findByNameRol(NameRol.CLIENT)).thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode("pass123")).thenReturn("encodedPass");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        AdminUserResponse response = userService.adminCreateUser(request);

        // Assert
        assertThat(response.getFullName()).isEqualTo("Test User");
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getRole()).isEqualTo(NameRol.CLIENT);
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void adminUpdateUser_WhenUpdatingSelfToDeactivate_ShouldThrowException() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setActive(false);

        User self = new User();
        self.setId(adminId);
        self.setId_rol(adminRole);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(self));

        // Act & Assert
        assertThatThrownBy(() -> userService.adminUpdateUser(adminId, request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No puedes desactivar tu propia cuenta");
    }

    @Test
    void adminUpdateUser_WhenUpdatingSelfToClient_ShouldThrowException() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setRole(NameRol.CLIENT);

        User self = new User();
        self.setId(adminId);
        self.setId_rol(adminRole);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(self));

        // Act & Assert
        assertThatThrownBy(() -> userService.adminUpdateUser(adminId, request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No puedes cambiar tu propio rol de administrador");
    }

    @Test
    void adminDeactivateUser_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        userService.adminDeactivateUser(userId, adminId);

        // Assert
        assertThat(user.getActive()).isFalse();
        assertThat(user.getFechaBaja()).isNotNull();
        assertThat(user.getEmail()).contains("disabled");
        verify(userRepository).save(user);
    }
}
