package com.chatbot.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    @NotNull(message = "El token de restablecimiento es requerido.")
    private UUID resetToken;

    @NotBlank(message = "La nueva contraseña es requerida.")
    @Size(min = 6, max = 50, message = "La nueva contraseña debe tener entre 6 y 50 caracteres.")
    private String newPassword;
}
