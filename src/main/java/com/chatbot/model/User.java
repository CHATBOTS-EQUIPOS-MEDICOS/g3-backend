package com.chatbot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "\"user\"")
public class User {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;
	 
	@Column()
	private String fullName;
	 
	@Column()
	private String email;
	 
	@Column()
	private String password;
	 
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	 
	@ManyToOne
	@JoinColumn(name = "id_rol", nullable = false)
	private Role id_rol;
	   
	@Column()
	private Boolean active = true;
	    
	@Column(name = "fecha_baja") 
	private LocalDateTime fechaBaja; 

	public User() {
	}

	public User(UUID id, String fullName, String email, String password, LocalDateTime createdAt, LocalDateTime updatedAt, Role id_rol, Boolean active, LocalDateTime fechaBaja) {
		this.id = id;
		this.fullName = fullName;
		this.email = email;
		this.password = password;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.id_rol = id_rol;
		this.active = active;
		this.fechaBaja = fechaBaja;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Role getId_rol() {
		return id_rol;
	}

	public void setId_rol(Role id_rol) {
		this.id_rol = id_rol;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public LocalDateTime getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(LocalDateTime fechaBaja) {
		this.fechaBaja = fechaBaja;
	}
}
