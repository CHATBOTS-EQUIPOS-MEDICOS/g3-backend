package com.chatbot.controller;

import com.chatbot.controller.dto.AuthResponse;
import com.chatbot.controller.dto.LoginRequest;
import com.chatbot.controller.dto.RegisterRequest;
import com.chatbot.model.User;
import com.chatbot.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        String token = authService.login(request);
        User user = authService.getUserByEmail(request.getEmail());
        String roleName = user.getId_rol().getNameRol().name();

        // 1. Cookie HttpOnly for JWT token (Secure against XSS)
        Cookie tokenCookie = new Cookie("token", token);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(false); // false to support local testing on http://localhost
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(86400); // 24 hours
        response.addCookie(tokenCookie);

        AuthResponse authResponse = AuthResponse.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(roleName)
                .message("Sesión iniciada correctamente.")
                .build();

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletResponse response) {
        // Clear JWT token cookie
        Cookie tokenCookie = new Cookie("token", null);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(false);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(0); // Delete
        response.addCookie(tokenCookie);

        AuthResponse authResponse = AuthResponse.builder()
                .message("Sesión cerrada correctamente.")
                .build();

        return ResponseEntity.ok(authResponse);
    }
}
