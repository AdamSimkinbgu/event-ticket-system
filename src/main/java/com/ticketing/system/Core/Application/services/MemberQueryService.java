package com.ticketing.system.Core.Application.services;

import java.util.Comparator;
import java.util.List;

import com.ticketing.system.Core.Application.dto.MemberSearchResultDTO;
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

    /**
     * Members whose username contains the given (case-insensitive) substring, sorted by
     * username. Blank input yields an empty list. Backs the admin "Send Messages" recipient
     * search (II.6.3.2) — returns only id + username, never a domain object.
     */
    public List<MemberSearchResultDTO> searchByUsername(String substring) {
        if (substring == null || substring.isBlank()) return List.of();
        String needle = substring.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(needle))
                .map(u -> new MemberSearchResultDTO(u.getUserId(), u.getUsername()))
                .sorted(Comparator.comparing(MemberSearchResultDTO::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
