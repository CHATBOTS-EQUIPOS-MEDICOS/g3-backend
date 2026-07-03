package com.chatbot.service;

import com.chatbot.controller.dto.AuthResponse;
import com.chatbot.controller.dto.LoginRequest;
import com.chatbot.controller.dto.RegisterRequest;
import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo ya está registrado.");
        }

        NameRol targetRoleName = request.getRole() != null ? request.getRole() : NameRol.CLIENT;
        Role role = roleRepository.findByNameRol(targetRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + targetRoleName));

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setId_rol(role);

        userRepository.save(user);

        AuthResponse response = new AuthResponse();
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setRole(role.getNameRol().name());
        response.setMessage("Usuario registrado exitosamente.");
        return response;
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        return jwtService.generateToken(user.getId().toString(), user.getId_rol().getNameRol().name());
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }
}
