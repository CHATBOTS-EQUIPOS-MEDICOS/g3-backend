package com.chatbot.controller.dto;

public class AuthResponse {
    private String fullName;
    private String email;
    private String role;
    private String message;

    public AuthResponse() {
    }

    public AuthResponse(String fullName, String email, String role, String message) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.message = message;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
