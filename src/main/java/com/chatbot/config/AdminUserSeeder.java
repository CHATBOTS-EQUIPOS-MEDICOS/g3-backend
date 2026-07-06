package com.chatbot.config;

import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import com.chatbot.model.User;
import com.chatbot.repository.RoleRepository;
import com.chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        java.util.List<User> adminUsers = userRepository.findAll().stream()
                .filter(u -> adminEmail.equalsIgnoreCase(u.getEmail()))
                .toList();

        if (!adminUsers.isEmpty()) {
            logger.info("El usuario administrador '{}' ya está configurado en la base de datos.", adminEmail);
            if (adminUsers.size() > 1) {
                logger.warn("Se detectaron {} usuarios administradores duplicados en la base de datos. Limpiando...", adminUsers.size());
                for (int i = 1; i < adminUsers.size(); i++) {
                    userRepository.delete(adminUsers.get(i));
                }
                logger.info("Usuarios administradores duplicados limpiados correctamente.");
            }
            return;
        }

        // Buscar el rol ADMIN
        Role adminRole = roleRepository.findByNameRol(NameRol.ADMIN)
                .orElseGet(() -> {
                    logger.warn("El rol ADMIN no existía en la base de datos. Creándolo...");
                    Role newRole = new Role();
                    newRole.setNameRol(NameRol.ADMIN);
                    logger.warn("Usuario admin creado exitosamente");
                    return roleRepository.save(newRole);
                });

        // Crear el administrador por defecto
        User adminUser = new User();
        adminUser.setFullName("Administrator");
        adminUser.setEmail(adminEmail);
        adminUser.setPassword(passwordEncoder.encode(adminPassword));
        adminUser.setId_rol(adminRole);
        adminUser.setActive(true);
        
        

        userRepository.save(adminUser);
        logger.info("Usuario administrador '{}' creado exitosamente como semilla.", adminEmail);
    }
}
