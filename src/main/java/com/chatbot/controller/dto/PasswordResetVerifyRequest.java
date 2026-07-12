package com.chatbot.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetVerifyRequest {

    @NotBlank(message = "El correo electrónico es requerido.")
    @Email(message = "El formato del correo electrónico es inválido.")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "El correo electrónico debe contener un símbolo '@' y un dominio con '.' (ejemplo: usuario@dominio.com)")
    private String email;

    @NotBlank(message = "El código es requerido.")
    @Size(min = 6, max = 6, message = "El código debe tener exactamente 6 caracteres.")
    private String code;
}
