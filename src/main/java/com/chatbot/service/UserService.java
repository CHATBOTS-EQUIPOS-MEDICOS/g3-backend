package com.chatbot.service;

import com.chatbot.controller.dto.UserRequest;
import com.chatbot.controller.dto.UserResponse;
import com.chatbot.model.User;
import com.chatbot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse getUserProfile(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("El usuario está desactivado.");
        }

        return new UserResponse(user.getEmail(), user.getFullName(), getRoleName(user));
    }

    public UserResponse updateUserProfile(UUID id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("El usuario está desactivado.");
        }

        boolean updated = false;

        // 1. Validar y actualizar nombre completo (fullName)
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            String fullName = request.getFullName().trim();
            if (fullName.length() < 3 || fullName.length() > 100) {
                throw new IllegalArgumentException("El nombre completo debe tener entre 3 y 100 caracteres.");
            }
            user.setFullName(fullName);
            updated = true;
        }

        // 2. Validar y actualizar contraseña (password)
        if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
            String newPassword = request.getNewPassword();

            if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
                throw new IllegalArgumentException("Debe ingresar la contraseña actual para establecer una nueva.");
            }

            // Verificar la contraseña anterior
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new IllegalArgumentException("La contraseña actual es incorrecta.");
            }

            // Verificar que la nueva contraseña no sea igual a la anterior
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                throw new IllegalArgumentException("La nueva contraseña no puede ser igual a la anterior.");
            }

            // Validar longitud de la nueva contraseña
            if (newPassword.length() < 6 || newPassword.length() > 50) {
                throw new IllegalArgumentException("La nueva contraseña debe tener entre 6 y 50 caracteres.");
            }

            // Validar seguridad: números, mayúsculas, minúsculas
            boolean hasDigit = false;
            boolean hasUpper = false;
            boolean hasLower = false;
            for (char c : newPassword.toCharArray()) {
                if (Character.isDigit(c)) {
                    hasDigit = true;
                } else if (Character.isUpperCase(c)) {
                    hasUpper = true;
                } else if (Character.isLowerCase(c)) {
                    hasLower = true;
                }
            }

            if (!hasDigit || !hasUpper || !hasLower) {
                throw new IllegalArgumentException("La nueva contraseña debe contener al menos un número, una letra mayúscula y una letra minúscula.");
            }

            // Encriptar y guardar nueva contraseña
            user.setPassword(passwordEncoder.encode(newPassword));
            updated = true;
        }

        if (updated) {
            userRepository.save(user);
        }

        return new UserResponse(user.getEmail(), user.getFullName(), getRoleName(user));
    }

    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("El usuario ya se encuentra desactivado.");
        }

        // Desactivar cuenta
        user.setActive(false);
        user.setFechaBaja(LocalDateTime.now());

        // Modificar el correo añadiendo "disabled" antes de la extensión del dominio (.com, .ar, etc.)
        String currentEmail = user.getEmail();
        if (currentEmail != null && currentEmail.contains("@")) {
            int atIndex = currentEmail.indexOf("@");
            String usernamePart = currentEmail.substring(0, atIndex);
            String domainPart = currentEmail.substring(atIndex + 1);
            
            int firstDotInDomain = domainPart.indexOf(".");
            if (firstDotInDomain != -1) {
                String domainName = domainPart.substring(0, firstDotInDomain);
                String extension = domainPart.substring(firstDotInDomain);
                user.setEmail(usernamePart + "@" + domainName + "disabled" + extension);
            } else {
                user.setEmail(currentEmail + "disabled");
            }
        }

        userRepository.save(user);
    }

    private String getRoleName(User user) {
        if (user.getId_rol() != null && user.getId_rol().getNameRol() != null) {
            return user.getId_rol().getNameRol().name();
        }
        return null;
    }
}
