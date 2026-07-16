package com.chatbot.config;

import com.chatbot.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) throws Exception {
        log.info("WebSocket handshake initiated for URI: {}", request.getURI());
        String token = null;

        // 1. Extraer del query parameter: ws://localhost:8080/ws/support?token=...
        String query = request.getURI().getQuery();
        if (query != null) {
            log.info("WebSocket handshake query string: {}", query);
            String[] params = query.split("&");
            for (String param : params) {
                int eqIdx = param.indexOf('=');
                if (eqIdx > 0) {
                    String key = param.substring(0, eqIdx);
                    String value = param.substring(eqIdx + 1);
                    if ("token".equals(key)) {
                        token = value;
                        log.info("Token extracted from WebSocket query parameters.");
                        break;
                    }
                }
            }
        }

        // Si el token está vacío en los query parameters, tratarlo como null para buscar en cookies
        if (token != null && token.trim().isEmpty()) {
            token = null;
        }

        // 2. Extraer de las cookies si no se encuentra en query params
        if (token == null && request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            Cookie[] cookies = httpServletRequest.getCookies();
            if (cookies != null) {
                log.info("WebSocket handshake: cookies found. Length: {}", cookies.length);
                for (Cookie cookie : cookies) {
                    if ("token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        log.info("Token extracted from WebSocket cookies.");
                        break;
                    }
                }
            } else {
                log.info("WebSocket handshake: no cookies found in request.");
            }
        }

        // Validar token y setear atributos en la sesión del websocket
        if (token != null) {
            try {
                String userId = jwtService.extractUsername(token);
                String role = jwtService.extractRole(token);
                log.info("WebSocket handshake: extracted userId: {}, role: {}", userId, role);
                
                if (userId != null && jwtService.isTokenValid(token, userId)) {
                    attributes.put("userId", userId);
                    attributes.put("role", role);
                    log.info("WebSocket handshake successful for user: {} with role: {}", userId, role);
                    return true;
                } else {
                    log.warn("WebSocket handshake: token is invalid for user: {}", userId);
                }
            } catch (Exception e) {
                log.error("WebSocket handshake: exception during token validation: {}", e.getMessage(), e);
            }
        } else {
            log.warn("WebSocket handshake rejected: no token found in query params or cookies.");
        }

        // Denegar el handshake si no está autenticado
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}
