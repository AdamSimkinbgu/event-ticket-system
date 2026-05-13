package com.ticketing.system.Core.Domain.users;

import java.util.Optional;

// Aggregate-root entry point for the User aggregate.
public interface IUserRepository {

    User getUserById(int targetId);

    boolean sendInvitation(int targetId, int companyId);

    void updateUser(User targetUser);

    // UC-11 / UC-12 — by-username lookup for registration uniqueness check + login.
    Optional<User> findByUsername(String username);

    // UC-11 — secondary uniqueness lookup.
    Optional<User> findByEmail(String email);

    // UC-11 — fast existence check used during registration validation.
    boolean existsByUsername(String username);

    // UC-11 — mint a fresh userId before constructing a User aggregate.
    int nextId();

    // UC-11 — persist newly-registered User (User aggregate creation).
    void save(User user);

    // II.6.2.x (Cancelled in v0) — defensive: defined for completeness.
    void delete(int userId);
}
