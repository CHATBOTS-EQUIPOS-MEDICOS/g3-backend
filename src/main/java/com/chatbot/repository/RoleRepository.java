package com.chatbot.repository;

import com.chatbot.model.NameRol;
import com.chatbot.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByNameRol(NameRol nameRol);
}
