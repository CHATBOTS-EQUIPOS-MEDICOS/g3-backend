package com.chatbot.controller;

import com.chatbot.controller.dto.AuthResponse;
import com.chatbot.controller.dto.LoginRequest;
import com.chatbot.controller.dto.RegisterRequest;
import com.chatbot.controller.dto.RecoveryRequest;
import com.chatbot.controller.dto.PasswordResetRequest;
import com.chatbot.controller.dto.PasswordResetVerifyRequest;
import com.chatbot.model.User;
import com.chatbot.service.AuthService;

import java.util.UUID;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
            HttpServletResponse response) {
        String token = authService.login(request);
        User user = authService.getUserByEmail(request.getEmail());
        String roleName = user.getId_rol().getNameRol().name();

        // 1. Cookie HttpOnly para el token JWT (Seguro contra XSS)
        Cookie tokenCookie = new Cookie("token", token);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(false); // false para soportar pruebas locales en http://localhost
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(86400); // 24 horas
        response.addCookie(tokenCookie);

        AuthResponse authResponse = new AuthResponse(
                user.getFullName(),
                user.getEmail(),
                roleName,
                "Sesión iniciada correctamente.");

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletResponse response) {
        // Limpiar la cookie del token JWT
        Cookie tokenCookie = new Cookie("token", null);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(false);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(0); // Eliminar
        response.addCookie(tokenCookie);

        AuthResponse authResponse = new AuthResponse(
                null,
                null,
                null,
                "Sesión cerrada correctamente.");

        return ResponseEntity.ok(authResponse);
    }

    // SOLICITAR CODIGO DE RECUPERACION
    @PostMapping("/recovery/request")
    public ResponseEntity<?> requestRecovery(@Valid @RequestBody RecoveryRequest request) {
        try {
            authService.requestPasswordRecovery(request);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Código de recuperación enviado al correo electrónico.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error interno del servidor: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    // FIN SOLICITAR CODIGO DE RECUPERACION

    // VERIFICAR CODIGO DE RECUPERACION
    @PostMapping("/recovery/verify")
    public ResponseEntity<?> verifyCode(@Valid @RequestBody PasswordResetVerifyRequest request) {
        try {
            UUID resetToken = authService.verifyPasswordResetCode(request);
            Map<String, Object> response = new HashMap<>();
            response.put("resetToken", resetToken);
            response.put("message", "Código verificado exitosamente.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error interno del servidor: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    // FIN VERIFICAR CODIGO DE RECUPERACION

    // RESTABLECER CONTRASEÑA
    @PostMapping("/recovery/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        try {
            authService.resetPassword(request);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Contraseña restablecida exitosamente.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error interno del servidor: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    // FIN RESTABLECER CONTRASEÑA
}
