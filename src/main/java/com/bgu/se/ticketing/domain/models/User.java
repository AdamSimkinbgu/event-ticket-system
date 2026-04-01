package com.bgu.se.ticketing.domain.models;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate root representing a registered user.
 *
 * <p>This class is framework-agnostic and must not depend on JPA, Spring, or any
 * infrastructure concern.
 */
public class User {

    private final String id;
    private String username;
    private String email;
    /** Hashed password – never stored as plain text. */
    private String passwordHash;
    private UserRole role;

    public User(String id, String username, String email, String passwordHash, UserRole role) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    /** Factory method that generates a new random ID. */
    public static User create(String username, String email, String passwordHash, UserRole role) {
        return new User(UUID.randomUUID().toString(), username, email, passwordHash, role);
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = Objects.requireNonNull(username, "username must not be null");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = Objects.requireNonNull(email, "email must not be null");
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', role=" + role + "}";
    }
}
