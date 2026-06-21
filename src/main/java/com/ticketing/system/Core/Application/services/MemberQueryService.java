package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Domain.users.IUserRepository;
import org.springframework.stereotype.Service;

/**
 * Read-only queries on the User aggregate that the presentation layer
 * needs but {@code AuthenticationService} (write-side) doesn't expose.
 *
 * <p>Today: a single {@link #usernameExists(String)} for the live
 * uniqueness check on {@code RegisterView}. Future read-side member
 * queries (profile lookups, presence checks, public-handle searches)
 * land here so {@code AuthenticationService}'s register/login/logout
 * surface stays focused.
 */
@Service
public class MemberQueryService {

    private final IUserRepository userRepository;

    public MemberQueryService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * True when a User with the given username already exists. Blank /
     * null input is treated as "doesn't exist" — the presenter handles
     * the format validation; this method is purely the existence
     * predicate.
     */
    public boolean usernameExists(String username) {
        if (username == null || username.isBlank()) return false;
        return userRepository.existsByUsername(username.trim());
    }
}
