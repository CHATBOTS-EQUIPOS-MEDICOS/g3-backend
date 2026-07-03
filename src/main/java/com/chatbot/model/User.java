package com.chatbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "\"user\"")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
	    private Boolean active;
	    
		@Column(name = "fecha_baja") 
	    private LocalDateTime fechaBaja; 

}
