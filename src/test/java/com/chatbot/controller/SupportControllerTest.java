package com.chatbot.controller;

import com.chatbot.model.*;
import com.chatbot.service.SupportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SupportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SupportService supportService;

    @InjectMocks
    private SupportController supportController;

    private UUID userId;
    private UUID sessionId;
    private com.chatbot.model.User clientEntity;
    private com.chatbot.model.User techEntity;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(supportController).build();
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        Role clientRole = new Role(1L, NameRol.CLIENT);
        clientEntity = new com.chatbot.model.User();
        clientEntity.setId(userId);
        clientEntity.setFullName("Test Client");
        clientEntity.setId_rol(clientRole);

        Role techRole = new Role(2L, NameRol.TECHNICIAN);
        techEntity = new com.chatbot.model.User();
        techEntity.setId(UUID.randomUUID());
        techEntity.setFullName("Test Technician");
        techEntity.setId_rol(techRole);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(UUID id, String role) {
        UserDetails userDetails = new User(id.toString(), "", Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void requestSupport_Success() throws Exception {
        // Arrange
        setSecurityContext(userId, "CLIENT");
        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setUser(clientEntity);
        session.setStatus(SupportStatus.WAITING);
        session.setCreatedAt(LocalDateTime.now());
        session.setSummary("Summary");

        when(supportService.findOrCreateActiveSession(userId)).thenReturn(session);

        // Act & Assert
        mockMvc.perform(post("/api/support/request")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.summary").value("Summary"));
    }

    @Test
    void getMessages_Admin_Success() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        setSecurityContext(adminId, "ADMIN");

        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setUser(clientEntity);
        session.setStatus(SupportStatus.RESOLVED); // Closed session

        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setSession(session);
        message.setSenderId(userId);
        message.setSenderType(SenderType.USER);
        message.setContent("Hello support");
        message.setCreatedAt(LocalDateTime.now());

        when(supportService.getSessionById(sessionId)).thenReturn(Optional.of(session));
        when(supportService.getMessagesForSession(sessionId)).thenReturn(Collections.singletonList(message));

        // Act & Assert
        mockMvc.perform(get("/api/support/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello support"));
    }

    @Test
    void getMessages_Technician_ForbiddenForClosedSession() throws Exception {
        // Arrange
        UUID techId = techEntity.getId();
        setSecurityContext(techId, "TECHNICIAN");

        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setUser(clientEntity);
        session.setSupport(techEntity);
        session.setStatus(SupportStatus.RESOLVED); // Closed session

        when(supportService.getSessionById(sessionId)).thenReturn(Optional.of(session));

        // Act & Assert
        mockMvc.perform(get("/api/support/sessions/" + sessionId + "/messages"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No tienes permiso para ver los mensajes de esta sesión. Los técnicos no pueden ver el historial de conversaciones de sesiones cerradas."));
    }

    @Test
    void getMessages_Technician_SuccessForActiveSession() throws Exception {
        // Arrange
        UUID techId = techEntity.getId();
        setSecurityContext(techId, "TECHNICIAN");

        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setUser(clientEntity);
        session.setSupport(techEntity);
        session.setStatus(SupportStatus.ACTIVE); // Active session

        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setSession(session);
        message.setSenderId(techId);
        message.setSenderType(SenderType.TECHNICIAN);
        message.setContent("Hello Client");
        message.setCreatedAt(LocalDateTime.now());

        when(supportService.getSessionById(sessionId)).thenReturn(Optional.of(session));
        when(supportService.getMessagesForSession(sessionId)).thenReturn(Collections.singletonList(message));

        // Act & Assert
        mockMvc.perform(get("/api/support/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello Client"));
    }

    @Test
    void acceptSession_Success() throws Exception {
        // Arrange
        UUID techId = techEntity.getId();
        setSecurityContext(techId, "TECHNICIAN");

        // Act & Assert
        mockMvc.perform(post("/api/support/sessions/" + sessionId + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Solicitud de aceptación encolada. Procesando..."));

        verify(supportService).queueAcceptance(sessionId, techId);
    }
}
