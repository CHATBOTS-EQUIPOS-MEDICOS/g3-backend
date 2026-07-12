package com.chatbot.repository;

import com.chatbot.model.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    Optional<PasswordResetCode> findFirstByEmailAndCodeAndUsedFalseOrderByExpirationTimeDesc(String email, String code);
}
