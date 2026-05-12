package com.ticketing.system.Core.Application.interfaces;

import java.util.Optional;

// Port for session / token management. Implemented in Infrastructure by JwtSessionManager.
// Used by AuthenticationService (UC-12/14) and NotificationDispatchService (UC-35/36 — online check).
public interface ISessionManager {

    // UC-12 — issue a token after successful login.
    String generateToken(int userId, String username);

    // Token validation — true if token is well-formed, signature valid, and not expired.
    // Throws InvalidTokenException for malformed; SessionExpiredException for expired.
    boolean validateToken(String token);

    // Returns the user ID encoded in the token.
    int extractUserId(String token);

    // Returns the username encoded in the token.
    String extractUsername(String token);

    // Quick expiry check without throwing.
    boolean isExpired(String token);

    // UC-12 — epoch millis at which the token expires (read from the exp claim).
    long extractExpiration(String token);

    // UC-14 — invalidate a token explicitly (server-side denylist or stateful session end).
    void invalidate(String token);

    // UC-35/36 — used by NotificationDispatchService to route live vs. PENDING storage.
    // Returns true if the user has a currently-active session.
    boolean isOnline(int userId);

    // Optional — returns the userId if the token is valid, empty otherwise.
    // Convenience for handlers that don't want to throw on bad tokens.
    Optional<Integer> tryExtractUserId(String token);
}
