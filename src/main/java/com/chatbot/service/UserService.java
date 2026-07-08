package com.chatbot.service;

import com.chatbot.controller.dto.AdminUserCreateRequest;
import com.chatbot.controller.dto.AdminUserResponse;
import com.chatbot.controller.dto.AdminUserUpdateRequest;
import com.chatbot.controller.dto.UserRequest;
import com.chatbot.controller.dto.UserResponse;
import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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

    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted((u1, u2) -> {
                    LocalDateTime t1 = u1.getCreatedAt();
                    LocalDateTime t2 = u2.getCreatedAt();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .map(this::mapToAdminUserResponse)
                .collect(Collectors.toList());
    }

    public AdminUserResponse adminCreateUser(AdminUserCreateRequest request) {
        if (userRepository.findFirstByEmail(request.getEmail().trim()).isPresent()) {
            throw new IllegalArgumentException("El correo ya está registrado.");
        }

        Role role = roleRepository.findByNameRol(request.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + request.getRole()));

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setId_rol(role);
        user.setActive(true);

        User savedUser = userRepository.save(user);
        return mapToAdminUserResponse(savedUser);
    }

    public AdminUserResponse adminUpdateUser(UUID id, AdminUserUpdateRequest request, UUID requesterId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        // Evitar que el administrador se modifique a sí mismo con ciertas restricciones
        boolean updatingSelf = id.equals(requesterId);

        boolean updated = false;

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            String fullName = request.getFullName().trim();
            if (fullName.length() < 3 || fullName.length() > 100) {
                throw new IllegalArgumentException("El nombre completo debe tener entre 3 y 100 caracteres.");
            }
            user.setFullName(fullName);
            updated = true;
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String email = request.getEmail().trim();
            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.findFirstByEmail(email).isPresent()) {
                    throw new IllegalArgumentException("El correo ya está registrado por otro usuario.");
                }
                user.setEmail(email);
                updated = true;
            }
        }

        if (request.getRole() != null) {
            if (updatingSelf && request.getRole() != NameRol.ADMIN) {
                throw new IllegalArgumentException("No puedes cambiar tu propio rol de administrador.");
            }
            Role role = roleRepository.findByNameRol(request.getRole())
                    .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + request.getRole()));
            user.setId_rol(role);
            updated = true;
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            String newPassword = request.getPassword();
            if (newPassword.length() < 6 || newPassword.length() > 50) {
                throw new IllegalArgumentException("La contraseña debe tener entre 6 y 50 caracteres.");
            }
            
            // Validar seguridad de la contraseña
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
                throw new IllegalArgumentException("La contraseña debe contener al menos un número, una letra mayúscula y una letra minúscula.");
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            updated = true;
        }

        if (request.getActive() != null) {
            if (updatingSelf && Boolean.FALSE.equals(request.getActive())) {
                throw new IllegalArgumentException("No puedes desactivar tu propia cuenta de administrador.");
            }
            if (request.getActive() != user.getActive()) {
                if (Boolean.FALSE.equals(request.getActive())) {
                    // Desactivar lógica
                    user.setActive(false);
                    user.setFechaBaja(LocalDateTime.now());
                    
                    // Modificar el correo añadiendo "disabled" antes de la extensión del dominio
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
                } else {
                    // Activar lógica
                    user.setActive(true);
                    user.setFechaBaja(null);
                    
                    // Restaurar correo si contiene "disabled"
                    String currentEmail = user.getEmail();
                    if (currentEmail != null && currentEmail.contains("disabled")) {
                        String restoredEmail = currentEmail.replace("disabled", "");
                        if (!userRepository.findFirstByEmail(restoredEmail).isPresent()) {
                            user.setEmail(restoredEmail);
                        }
                    }
                }
                updated = true;
            }
        }

        if (updated) {
            user = userRepository.save(user);
        }

        return mapToAdminUserResponse(user);
    }

    public void adminDeactivateUser(UUID id, UUID requesterId) {
        if (id.equals(requesterId)) {
            throw new IllegalArgumentException("No puedes desactivar tu propia cuenta de administrador.");
        }
        deactivateUser(id);
    }

    public AdminUserResponse adminActivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("El usuario ya se encuentra activo.");
        }

        user.setActive(true);
        user.setFechaBaja(null);

        // Restaurar correo si contiene "disabled"
        String currentEmail = user.getEmail();
        if (currentEmail != null && currentEmail.contains("disabled")) {
            String restoredEmail = currentEmail.replace("disabled", "");
            if (userRepository.findFirstByEmail(restoredEmail).isPresent()) {
                throw new IllegalArgumentException("No se puede reactivar el usuario porque su correo original (" + restoredEmail + ") ya está registrado por otra cuenta activa.");
            }
            user.setEmail(restoredEmail);
        }

        User savedUser = userRepository.save(user);
        return mapToAdminUserResponse(savedUser);
    }

    private AdminUserResponse mapToAdminUserResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getId_rol() != null ? user.getId_rol().getNameRol() : null)
                .active(user.getActive())
                .fechaBaja(user.getFechaBaja())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
