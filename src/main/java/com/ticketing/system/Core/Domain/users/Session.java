package com.ticketing.system.Core.Domain.users;

import java.time.Instant;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * Session aggregate — the unified identity record for both Member and Guest
 * sessions. Part of the auth rework (see docs/auth-rework-plan.md).
 *
 * <ul>
 *   <li><b>Member session:</b> {@code userId != null}. Issued by
 *       {@code AuthenticationService.login}; the matching JWT carries
 *       {@code sid = sessionId}.</li>
 *   <li><b>Guest session:</b> {@code userId == null}. Started anonymously via
 *       {@code AuthenticationService.startGuestSession}; the raw sessionId is
 *       the credential (no JWT).</li>
 * </ul>
 *
 * <p>A Guest session can be promoted to a Member session in-place via
 * {@link #promoteTo(int, Instant)} — the sessionId is preserved, so any cart
 * or other state already attached to it survives the transition.
 */
public class Session implements InvariantChecked {

    private final String sessionId;
    private Integer userId;
    private final Instant createdAt;
    private Instant lastSeenAt;
    private Instant expiresAt;

    public Session(String sessionId, Integer userId, Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
        this.expiresAt = expiresAt;
        checkInvariants();
    }

    public String getSessionId() {
        return sessionId;
    }

    /** {@code null} for Guest sessions. */
    public Integer getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isMember() {
        return userId != null;
    }

    public boolean isGuest() {
        return userId == null;
    }

    /**
     * Promotes a Guest session to a Member session in-place. The sessionId is
     * preserved, so any cart attached to it survives.
     *
     * @throws IllegalStateException if this session is already a Member
     */
    public void promoteTo(int newUserId, Instant newExpiresAt) {
        if (this.userId != null) {
            throw new IllegalStateException("session already a member");
        }
        if (newExpiresAt == null) {
            throw new IllegalArgumentException("newExpiresAt must not be null");
        }
        this.userId = newUserId;
        this.expiresAt = newExpiresAt;
    }

    /** Records activity. Used by the idle-timeout flow for Guest sessions. */
    public void touch(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        this.lastSeenAt = now;
    }

    /** Extends the expiry. Used by the idle-timeout flow when a Guest is active. */
    public void extendExpiry(Instant newExpiresAt) {
        if (newExpiresAt == null) {
            throw new IllegalArgumentException("newExpiresAt must not be null");
        }
        this.expiresAt = newExpiresAt;
    }

    /** Boundary is inclusive: {@code now == expiresAt} counts as expired. */
    public boolean isExpiredAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        return !now.isBefore(expiresAt);
    }

    @Override
    public void checkInvariants() {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("Session invariant violated: sessionId must be non-blank");
        }
        if (createdAt == null) {
            throw new IllegalStateException("Session invariant violated: createdAt must not be null");
        }
        if (lastSeenAt == null) {
            throw new IllegalStateException("Session invariant violated: lastSeenAt must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalStateException("Session invariant violated: expiresAt must not be null");
        }
        if (lastSeenAt.isBefore(createdAt)) {
            throw new IllegalStateException("Session invariant violated: lastSeenAt must be >= createdAt");
        }
        // Note: userId is intentionally NOT bounds-checked here. The Session domain stays
        // permissive about userId values — positive/zero/negative is the User aggregate's
        // concern. See SessionTest#memberSession_acceptsZeroAndNegativeUserIds.
    }
}
