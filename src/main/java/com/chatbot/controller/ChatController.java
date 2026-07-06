package com.chatbot.controller;

import com.chatbot.service.ChatService;
import com.chatbot.service.ChatService.ChatAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // Permitir una conexión sencilla desde el frontend
@Slf4j
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    public record AskRequest(
        String question
    ) {}

    /**
     * Endpoint para realizar preguntas basadas en los manuales de equipos médicos subidos.
     * Espera un cuerpo de petición JSON como: { "question": "¿Cuál es la capacidad de la batería del Modelo X?" }
     */
    @PostMapping("/ask")
    public ResponseEntity<?> askQuestion(@RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().strip().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The question field must not be empty."));
        }

        try {
            ChatAnswer answer = chatService.askQuestion(request.question().trim());
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            log.error("Error processing chat question", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to answer the question: " + e.getMessage()
            ));
        }
    }
}
