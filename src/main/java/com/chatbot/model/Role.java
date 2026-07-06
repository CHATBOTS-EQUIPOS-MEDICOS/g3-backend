package com.chatbot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "role")
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true)
    private NameRol nameRol;

    public Role() {
    }

    public Role(Long id, NameRol nameRol) {
        this.id = id;
        this.nameRol = nameRol;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NameRol getNameRol() {
        return nameRol;
    }

    public void setNameRol(NameRol nameRol) {
        this.nameRol = nameRol;
    }
}
