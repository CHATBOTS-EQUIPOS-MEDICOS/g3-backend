package com.chatbot.repository;

import com.chatbot.model.EmployeeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmployeeDetailRepository extends JpaRepository<EmployeeDetail, UUID> {
}
