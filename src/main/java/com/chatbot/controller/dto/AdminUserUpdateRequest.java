package com.chatbot.controller.dto;

import com.chatbot.model.NameRol;
import jakarta.validation.constraints.Email;
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
public class AdminUserUpdateRequest {

    @Size(min = 3, max = 100, message = "El nombre completo debe tener entre 3 y 100 caracteres.")
    private String fullName;

    @Email(message = "El formato del correo electrónico es inválido.")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "El correo electrónico debe contener un símbolo '@' y un dominio con '.' (ejemplo: usuario@dominio.com)")
    private String email;

    @Size(min = 6, max = 50, message = "La contraseña debe tener entre 6 y 50 caracteres.")
    private String password;

    private NameRol role;

    private Boolean active;
}
