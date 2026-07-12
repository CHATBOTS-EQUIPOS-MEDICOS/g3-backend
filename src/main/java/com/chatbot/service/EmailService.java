package com.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendRecoveryCode(String toEmail, String code) {
        logger.info("Enviando correo de recuperación de contraseña a: {}", toEmail);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Código de Recuperación de Contraseña");
            message.setText("Hola,\n\n"
                    + "Has solicitado restablecer tu contraseña. Utiliza el siguiente código de verificación de un solo uso:\n\n"
                    + code + "\n\n"
                    + "Este código tiene una validez de 30 minutos. Si no realizaste esta solicitud, puedes ignorar este correo de forma segura.\n\n"
                    + "Saludos,\nSoporte Técnico");
            mailSender.send(message);
            logger.info("Correo enviado exitosamente a: {}", toEmail);
        } catch (Exception e) {
            logger.error("Error al enviar el correo a {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("No se pudo enviar el correo de recuperación. Por favor, intente más tarde.");
        }
    }
}
