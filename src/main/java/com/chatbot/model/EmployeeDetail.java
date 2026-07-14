package com.chatbot.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "employee_detail")
public class EmployeeDetail {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "work_days", nullable = false)
    private String workDays;

    @Column(name = "work_hours", nullable = false)
    private String workHours;

    public EmployeeDetail() {
    }

    public EmployeeDetail(User user, String workDays, String workHours) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
        this.workDays = workDays;
        this.workHours = workHours;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }

    public String getWorkDays() {
        return workDays;
    }

    public void setWorkDays(String workDays) {
        this.workDays = workDays;
    }

    public String getWorkHours() {
        return workHours;
    }

    public void setWorkHours(String workHours) {
        this.workHours = workHours;
    }
}
