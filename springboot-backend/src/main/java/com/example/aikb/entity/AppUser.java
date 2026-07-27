package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(
            UUID id,
            String username,
            String passwordHash,
            String displayName,
            String department,
            String role,
            Instant createdAt
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.department = department;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public String department() {
        return department;
    }

    public String role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
