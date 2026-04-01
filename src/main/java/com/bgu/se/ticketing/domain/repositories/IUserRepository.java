package com.bgu.se.ticketing.domain.repositories;

import com.bgu.se.ticketing.domain.models.User;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository interface for {@link User} aggregates.
 *
 * <p>This interface belongs to the domain layer. It is technology-agnostic and
 * expresses the domain's data access requirements. Concrete implementations live
 * in the infrastructure layer.
 */
public interface IUserRepository {

    /** Persists a new user or updates an existing one. */
    User save(User user);

    /** Finds a user by its unique identifier. */
    Optional<User> findById(String id);

    /** Finds a user by username (used during authentication). */
    Optional<User> findByUsername(String username);

    /** Finds a user by email address. */
    Optional<User> findByEmail(String email);

    /** Returns all users in the system. */
    List<User> findAll();

    /** Deletes a user by its unique identifier. */
    void deleteById(String id);

    /** Checks whether a user with the given username already exists. */
    boolean existsByUsername(String username);

    /** Checks whether a user with the given email already exists. */
    boolean existsByEmail(String email);
}
