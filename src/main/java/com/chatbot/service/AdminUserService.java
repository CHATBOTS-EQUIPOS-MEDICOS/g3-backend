package com.chatbot.service;

import com.chatbot.controller.dto.AdminUserCreateRequest;
import com.chatbot.controller.dto.AdminUserResponse;
import com.chatbot.controller.dto.AdminUserUpdateRequest;
import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.model.EmployeeDetail;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    public AdminUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserService userService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
    }

    private boolean isEmployeeRole(NameRol role) {
        return role == NameRol.TECHNICIAN;
    }

    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted((u1, u2) -> {
                    LocalDateTime t1 = u1.getCreatedAt();
                    LocalDateTime t2 = u2.getCreatedAt();
                    if (t1 == null && t2 == null)
                        return 0;
                    if (t1 == null)
                        return 1;
                    if (t2 == null)
                        return -1;
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

        if (isEmployeeRole(request.getRole())) {
            if (request.getWorkDays() == null || request.getWorkDays().trim().isEmpty()) {
                throw new IllegalArgumentException("Los días de trabajo son requeridos para un técnico.");
            }
            if (request.getWorkHours() == null || request.getWorkHours().trim().isEmpty()) {
                throw new IllegalArgumentException("El horario de trabajo es requerido para un técnico.");
            }
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setId_rol(role);
        user.setActive(true);

        if (isEmployeeRole(request.getRole())) {
            EmployeeDetail detail = new EmployeeDetail(user, request.getWorkDays().trim(),
                    request.getWorkHours().trim());
            user.setEmployeeDetail(detail);
        }

        User savedUser = userRepository.save(user);
        return mapToAdminUserResponse(savedUser);
    }

    public AdminUserResponse adminUpdateUser(UUID id, AdminUserUpdateRequest request, UUID requesterId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

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
                    throw new IllegalArgumentException("El correo electronico ya está registrado por otro usuario.");
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
                throw new IllegalArgumentException(
                        "La contraseña debe contener al menos un número, una letra mayúscula y una letra minúscula.");
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
                    user.setActive(false);
                    user.setFechaBaja(LocalDateTime.now());

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
                    user.setActive(true);
                    user.setFechaBaja(null);

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

        // Employee detail logic
        NameRol currentRoleName = user.getId_rol() != null ? user.getId_rol().getNameRol() : null;
        if (isEmployeeRole(currentRoleName)) {
            EmployeeDetail detail = user.getEmployeeDetail();
            if (detail == null) {
                if (request.getWorkDays() == null || request.getWorkDays().trim().isEmpty()) {
                    throw new IllegalArgumentException("Los días de trabajo son requeridos para un técnico.");
                }
                if (request.getWorkHours() == null || request.getWorkHours().trim().isEmpty()) {
                    throw new IllegalArgumentException("El horario de trabajo es requerido para un técnico.");
                }
                detail = new EmployeeDetail(user, request.getWorkDays().trim(), request.getWorkHours().trim());
                user.setEmployeeDetail(detail);
                updated = true;
            } else {
                if (request.getWorkDays() != null) {
                    if (request.getWorkDays().trim().isEmpty()) {
                        throw new IllegalArgumentException("Los días de trabajo no pueden estar vacíos.");
                    }
                    detail.setWorkDays(request.getWorkDays().trim());
                    updated = true;
                }
                if (request.getWorkHours() != null) {
                    if (request.getWorkHours().trim().isEmpty()) {
                        throw new IllegalArgumentException("El horario de trabajo no puede estar vacío.");
                    }
                    detail.setWorkHours(request.getWorkHours().trim());
                    updated = true;
                }
            }
        } else {
            if (user.getEmployeeDetail() != null) {
                user.setEmployeeDetail(null);
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
        userService.deactivateUser(id);
    }

    public AdminUserResponse adminActivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("El usuario ya se encuentra activo.");
        }

        user.setActive(true);
        user.setFechaBaja(null);

        String currentEmail = user.getEmail();
        if (currentEmail != null && currentEmail.contains("disabled")) {
            String restoredEmail = currentEmail.replace("disabled", "");
            if (userRepository.findFirstByEmail(restoredEmail).isPresent()) {
                throw new IllegalArgumentException("No se puede reactivar el usuario porque su correo original ("
                        + restoredEmail + ") ya está registrado por otra cuenta activa.");
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
                .workDays(user.getEmployeeDetail() != null ? user.getEmployeeDetail().getWorkDays() : null)
                .workHours(user.getEmployeeDetail() != null ? user.getEmployeeDetail().getWorkHours() : null)
                .build();
    }
}
