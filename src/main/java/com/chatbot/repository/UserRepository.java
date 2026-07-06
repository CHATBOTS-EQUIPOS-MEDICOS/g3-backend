package com.chatbot.repository;

import com.chatbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chatbot.model.NameRol;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findFirstByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.id_rol.nameRol = :roleName AND u.active = true")
    List<User> findActiveByRole(@Param("roleName") NameRol roleName);
}
