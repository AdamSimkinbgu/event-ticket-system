package com.ticketing.system.Core.Domain.users;

import java.util.Optional;

/** Aggregate-root entry point for the User aggregate. */
public interface IUserRepository {

    /**
     * @throws com.ticketing.system.Core.Domain.exceptions.UserNotFoundException if
     *                                                                           no
     *                                                                           user
     *                                                                           with
     *                                                                           that
     *                                                                           id
     *                                                                           exists
     */
    User getUserById(int targetId);

    boolean sendInvitation(int targetId, int companyId);

    void sendOwnerInvitation(int targetId, int companyId);

    /** Persists changes to an existing User. */
    void updateUser(User targetUser);

    /** By-username lookup. Used by UC-11 (uniqueness check) and UC-12 (login). */
    Optional<User> findByUsername(String username);

    /** By-email lookup. Used by UC-11 as a secondary uniqueness check. */
    Optional<User> findByEmail(String email);

    /** Fast existence check used during UC-11 registration validation. */
    boolean existsByUsername(String username);

    /**
     * Mints a fresh userId. Storage owns ID generation rather than the service.
     * UC-11.
     */
    int nextId();

    /** Persists a newly-registered User. UC-11. */
    void save(User user);

    /** Removes a user by id. Defensive — II.6.2.x is cancelled in v0. */
    void delete(int userId);
}
