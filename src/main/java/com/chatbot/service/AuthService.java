package com.chatbot.service;

import com.chatbot.controller.dto.AuthResponse;
import com.chatbot.controller.dto.LoginRequest;
import com.chatbot.controller.dto.RegisterRequest;
import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.model.PasswordResetCode;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import com.chatbot.repository.PasswordResetCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.chatbot.controller.dto.RecoveryRequest;
import com.chatbot.controller.dto.PasswordResetRequest;
import com.chatbot.controller.dto.PasswordResetVerifyRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final EmailService emailService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PasswordResetCodeRepository passwordResetCodeRepository,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.emailService = emailService;
    }

    // REGISTRAR USUARIO
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findFirstByEmail(request.getEmail()).isPresent()) {
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

    // AUTENTIFICAR USUARIO
    public String login(LoginRequest request) {
        User user = userRepository.findFirstByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        return jwtService.generateToken(user.getId().toString(), user.getId_rol().getNameRol().name());
    }

    // OBTENER USUARIO POR EMAIL
    public User getUserByEmail(String email) {
        return userRepository.findFirstByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }

    public void requestPasswordRecovery(RecoveryRequest request) {
        String email = request.getEmail().trim();
        User user = userRepository.findFirstByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("El correo electrónico no está registrado."));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("La cuenta de usuario está desactivada.");
        }

        String code = generateRandomAlphanumericCode(6);
        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(30);

        PasswordResetCode resetCode = new PasswordResetCode(email, code, expirationTime);
        passwordResetCodeRepository.save(resetCode);

        emailService.sendRecoveryCode(email, code);
    }

    // VERIFICAR CODIGO DE RECUPERACION
    public UUID verifyPasswordResetCode(PasswordResetVerifyRequest request) {
        String email = request.getEmail().trim();
        String code = request.getCode().trim().toUpperCase();

        // Buscar el código activo más reciente
        PasswordResetCode resetCode = passwordResetCodeRepository
                .findFirstByEmailAndCodeAndUsedFalseOrderByExpirationTimeDesc(email, code)
                .orElseThrow(() -> new IllegalArgumentException("El código de verificación es inválido o ya fue utilizado."));

        // Verificar expiración (validez de 30 minutos)
        if (resetCode.getExpirationTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El código de verificación ha expirado.");
        }

        // Marcar el código como verificado
        resetCode.setVerified(true);
        passwordResetCodeRepository.save(resetCode);

        return resetCode.getId();
    }
    // FIN VERIFICAR CODIGO DE RECUPERACION

    // RESETEAR CONTRASEÑA
    public void resetPassword(PasswordResetRequest request) {
        UUID resetToken = request.getResetToken();
        String newPassword = request.getNewPassword();

        // Buscar el código por su ID (UUID)
        PasswordResetCode resetCode = passwordResetCodeRepository.findById(resetToken)
                .orElseThrow(() -> new IllegalArgumentException("Token de restablecimiento inválido."));

        // Validar que esté verificado y no usado
        if (Boolean.FALSE.equals(resetCode.getVerified())) {
            throw new IllegalArgumentException("El token de restablecimiento no ha sido verificado.");
        }
        if (Boolean.TRUE.equals(resetCode.getUsed())) {
            throw new IllegalArgumentException("El token de restablecimiento ya fue utilizado.");
        }

        // Verificar expiración (validez de 30 minutos desde la generación original)
        if (resetCode.getExpirationTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El token de restablecimiento ha expirado.");
        }

        // Buscar usuario por el correo del token
        User user = userRepository.findFirstByEmail(resetCode.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("La cuenta de usuario está desactivada.");
        }

        // Validar seguridad de la nueva contraseña
        if (newPassword.length() < 6 || newPassword.length() > 50) {
            throw new IllegalArgumentException("La nueva contraseña debe tener entre 6 y 50 caracteres.");
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
                    "La nueva contraseña debe contener al menos un número, una letra mayúscula y una letra minúscula.");
        }

        // Encriptar y guardar nueva contraseña
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Deshabilitar el código permanentemente
        resetCode.setUsed(true);
        passwordResetCodeRepository.save(resetCode);
    }
    // FIN RESETEAR CONTRASEÑA

    // GENERAR CODIGO DE RECUPERACION
    private String generateRandomAlphanumericCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    // FIN GENERAR CODIGO DE RECUPERACION
}
