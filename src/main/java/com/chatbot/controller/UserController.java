package com.chatbot.controller;

import com.chatbot.controller.dto.UserRequest;
import com.chatbot.controller.dto.UserResponse;
import com.chatbot.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            UUID userId = UUID.fromString(principal.getName());
            UserResponse response = userService.getUserProfile(userId);
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

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            Principal principal,
            @RequestBody UserRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            UUID userId = UUID.fromString(principal.getName());
            UserResponse response = userService.updateUserProfile(userId, request);
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

    @DeleteMapping("/me")
    public ResponseEntity<?> deactivateAccount(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            UUID userId = UUID.fromString(principal.getName());
            userService.deactivateUser(userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Cuenta desactivada exitosamente.");
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
}
