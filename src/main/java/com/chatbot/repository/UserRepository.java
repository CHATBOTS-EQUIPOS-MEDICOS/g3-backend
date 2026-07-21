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

    @Query("SELECT COUNT(u) FROM User u WHERE u.id_rol.nameRol = :roleName")
    long countByRoleName(@Param("roleName") NameRol roleName);

    @Query("SELECT COUNT(u) FROM User u WHERE u.id_rol.nameRol = :roleName AND u.active = :active")
    long countByRoleNameAndActive(@Param("roleName") NameRol roleName, @Param("active") Boolean active);

    @Query("SELECT u.id, u.fullName, u.email, (SELECT COUNT(s) FROM SupportSession s WHERE s.support = u) " +
           "FROM User u WHERE u.id_rol.nameRol = :roleName AND u.active = true")
    List<Object[]> findActiveTechniciansWithSessionCount(@Param("roleName") NameRol roleName);
}
