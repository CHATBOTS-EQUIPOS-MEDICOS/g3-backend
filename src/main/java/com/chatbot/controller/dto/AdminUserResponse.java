package com.chatbot.controller.dto;

import com.chatbot.model.NameRol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private UUID id;
    private String fullName;
    private String email;
    private NameRol role;
    private Boolean active;
    private LocalDateTime fechaBaja;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
