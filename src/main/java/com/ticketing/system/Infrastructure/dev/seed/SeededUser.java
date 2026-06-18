package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Domain.users.User;

/**
 * A seeded user plus their freshly-issued JWT. Downstream seeders use
 * the token to make service calls on the user's behalf (register a
 * company, add an event, place a reservation, etc.) and the User
 * entity for ids referenced in cross-aggregate DTOs.
 */
public record SeededUser(User user, String token) {

    public int userId() {
        return user.getUserId();
    }

    public String username() {
        return user.getUsername();
    }
}
