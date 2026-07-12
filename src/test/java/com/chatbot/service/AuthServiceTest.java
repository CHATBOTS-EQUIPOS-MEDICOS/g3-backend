package com.chatbot.service;

import com.chatbot.controller.dto.RecoveryRequest;
import com.chatbot.controller.dto.PasswordResetRequest;
import com.chatbot.model.PasswordResetCode;
import com.chatbot.model.User;
import com.chatbot.repository.PasswordResetCodeRepository;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chatbot.controller.dto.PasswordResetVerifyRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordResetCodeRepository passwordResetCodeRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @Test
    void requestPasswordRecovery_WhenUserNotFound_ShouldThrowException() {
        // Arrange
        RecoveryRequest request = new RecoveryRequest("notfound@example.com");
        when(userRepository.findFirstByEmail(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.requestPasswordRecovery(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El correo electrónico no está registrado");
    }

    @Test
    void requestPasswordRecovery_WhenUserInactive_ShouldThrowException() {
        // Arrange
        RecoveryRequest request = new RecoveryRequest("inactive@example.com");
        User user = new User();
        user.setActive(false);
        when(userRepository.findFirstByEmail(any())).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> authService.requestPasswordRecovery(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La cuenta de usuario está desactivada");
    }

    @Test
    void requestPasswordRecovery_Success() {
        // Arrange
        RecoveryRequest request = new RecoveryRequest("user@example.com");
        User user = new User();
        user.setActive(true);
        when(userRepository.findFirstByEmail(any())).thenReturn(Optional.of(user));

        // Act
        authService.requestPasswordRecovery(request);

        // Assert
        verify(passwordResetCodeRepository, times(1)).save(any(PasswordResetCode.class));
        verify(emailService, times(1)).sendRecoveryCode(eq("user@example.com"), anyString());
    }

    @Test
    void verifyPasswordResetCode_WhenCodeInvalid_ShouldThrowException() {
        // Arrange
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest("user@example.com", "123456");
        when(passwordResetCodeRepository.findFirstByEmailAndCodeAndUsedFalseOrderByExpirationTimeDesc(any(), any()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyPasswordResetCode(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El código de verificación es inválido o ya fue utilizado");
    }

    @Test
    void verifyPasswordResetCode_WhenCodeExpired_ShouldThrowException() {
        // Arrange
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest("user@example.com", "123456");
        PasswordResetCode expiredCode = new PasswordResetCode("user@example.com", "123456", LocalDateTime.now().minusMinutes(1));

        when(passwordResetCodeRepository.findFirstByEmailAndCodeAndUsedFalseOrderByExpirationTimeDesc(any(), any()))
                .thenReturn(Optional.of(expiredCode));

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyPasswordResetCode(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El código de verificación ha expirado");
    }

    @Test
    void verifyPasswordResetCode_CaseInsensitiveSuccess() {
        // Arrange
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest("user@example.com", "abc123");
        PasswordResetCode validCode = new PasswordResetCode("user@example.com", "ABC123", LocalDateTime.now().plusMinutes(30));
        UUID expectedId = UUID.randomUUID();
        validCode.setId(expectedId);

        when(passwordResetCodeRepository.findFirstByEmailAndCodeAndUsedFalseOrderByExpirationTimeDesc("user@example.com", "ABC123"))
                .thenReturn(Optional.of(validCode));
        when(passwordResetCodeRepository.save(validCode)).thenReturn(validCode);

        // Act
        UUID resultToken = authService.verifyPasswordResetCode(request);

        // Assert
        assertThat(resultToken).isEqualTo(expectedId);
        assertThat(validCode.getVerified()).isTrue();
        verify(passwordResetCodeRepository, times(1)).save(validCode);
    }

    @Test
    void resetPassword_WhenTokenNotFound_ShouldThrowException() {
        // Arrange
        UUID token = UUID.randomUUID();
        PasswordResetRequest request = new PasswordResetRequest(token, "NewPassword123");
        when(passwordResetCodeRepository.findById(token)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token de restablecimiento inválido");
    }

    @Test
    void resetPassword_WhenTokenNotVerified_ShouldThrowException() {
        // Arrange
        UUID token = UUID.randomUUID();
        PasswordResetRequest request = new PasswordResetRequest(token, "NewPassword123");
        PasswordResetCode code = new PasswordResetCode("user@example.com", "ABC123", LocalDateTime.now().plusMinutes(30));
        code.setId(token);
        code.setVerified(false);

        when(passwordResetCodeRepository.findById(token)).thenReturn(Optional.of(code));

        // Act & Assert
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El token de restablecimiento no ha sido verificado");
    }

    @Test
    void resetPassword_WhenTokenAlreadyUsed_ShouldThrowException() {
        // Arrange
        UUID token = UUID.randomUUID();
        PasswordResetRequest request = new PasswordResetRequest(token, "NewPassword123");
        PasswordResetCode code = new PasswordResetCode("user@example.com", "ABC123", LocalDateTime.now().plusMinutes(30));
        code.setId(token);
        code.setVerified(true);
        code.setUsed(true);

        when(passwordResetCodeRepository.findById(token)).thenReturn(Optional.of(code));

        // Act & Assert
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El token de restablecimiento ya fue utilizado");
    }

    @Test
    void resetPassword_WhenTokenExpired_ShouldThrowException() {
        // Arrange
        UUID token = UUID.randomUUID();
        PasswordResetRequest request = new PasswordResetRequest(token, "NewPassword123");
        PasswordResetCode code = new PasswordResetCode("user@example.com", "ABC123", LocalDateTime.now().minusMinutes(1));
        code.setId(token);
        code.setVerified(true);
        code.setUsed(false);

        when(passwordResetCodeRepository.findById(token)).thenReturn(Optional.of(code));

        // Act & Assert
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El token de restablecimiento ha expirado");
    }

    @Test
    void resetPassword_Success() {
        // Arrange
        UUID token = UUID.randomUUID();
        PasswordResetRequest request = new PasswordResetRequest(token, "NewSecurePass123");
        PasswordResetCode code = new PasswordResetCode("user@example.com", "ABC123", LocalDateTime.now().plusMinutes(30));
        code.setId(token);
        code.setVerified(true);
        code.setUsed(false);
        User user = new User();
        user.setActive(true);

        when(passwordResetCodeRepository.findById(token)).thenReturn(Optional.of(code));
        when(userRepository.findFirstByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewSecurePass123")).thenReturn("encodedPassword");

        // Act
        authService.resetPassword(request);

        // Assert
        verify(userRepository, times(1)).save(user);
        verify(passwordResetCodeRepository, times(1)).save(code);
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
        assertThat(code.getUsed()).isTrue();
    }
}
