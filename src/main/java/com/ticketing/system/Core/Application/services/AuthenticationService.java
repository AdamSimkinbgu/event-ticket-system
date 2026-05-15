package com.ticketing.system.Core.Application.services;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.GuestSessionDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.LogoutRequestDTO;
import com.ticketing.system.Core.Application.dto.RefreshTokenRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateEmailException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.GuestSessionRequiredException;
import com.ticketing.system.Core.Domain.exceptions.InvalidEmailFormatException;
import com.ticketing.system.Core.Domain.exceptions.WeakPasswordException;
import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Session;
import com.ticketing.system.Core.Domain.users.User;

/**
 * Application service for the auth slice — registration (UC-11), login
 * (UC-12), and logout (UC-14), plus guest-session lifecycle.
 *
 * <p>Per the spec, a visitor is always a Guest first: they call
 * {@link #startGuestSession()} to mint a Session row, then optionally
 * {@link #register(RegisterRequestDTO)} (which keeps them Guest) and
 * {@link #login(LoginRequestDTO)} (which promotes their existing Session
 * to a Member session in place).
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final IUserRepository userRepository;
    private final IPasswordHasher passwordHasher;
    private final ISessionManager sessionManager;
    private final ISessionRepository sessionRepository;
    private final Clock clock;
    private final long guestIdleMinutes;
    private final long memberTtlMinutes;

    public AuthenticationService(
            IUserRepository userRepository,
            IPasswordHasher passwordHasher,
            ISessionManager sessionManager,
            ISessionRepository sessionRepository,
            Clock clock,
            @Value("${session.guest-idle-timeout-minutes}") long guestIdleMinutes,
            @Value("${session.member-ttl-minutes}") long memberTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.guestIdleMinutes = guestIdleMinutes;
        this.memberTtlMinutes = memberTtlMinutes;
    }

    /** Delegates to {@link ISessionManager#extractUserId}. */
    public int extractUserId(String token) {
        return sessionManager.extractUserId(token);
    }

    /** Delegates to {@link ISessionManager#validateToken}. */
    public boolean validateToken(String token) {
        return sessionManager.validateToken(token);
    }

    // ------------------------------------------------------------------
    // Guest session lifecycle
    // ------------------------------------------------------------------

    /**
     * Mints a new Guest session. Entry point for any visitor before
     * register/login.
     */
    public GuestSessionDTO startGuestSession() {
        Instant now = clock.instant();
        Instant expiry = now.plus(guestIdleMinutes, ChronoUnit.MINUTES);
        String sid = UUID.randomUUID().toString();
        sessionRepository.save(new Session(sid, null, now, expiry));
        log.info("guest session started sid={}", sid);
        return new GuestSessionDTO(sid, now);
    }

    /**
     * Explicitly ends a Guest session. Idempotent — unknown / null / blank
     * input is a no-op. Attached cart cleanup is the sweeper's
     * responsibility (Phase 5).
     */
    public void endGuestSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        sessionRepository.delete(sessionId);
        log.info("guest session ended sid={}", sessionId);
    }

    // ------------------------------------------------------------------
    // Register (UC-11)
    // ------------------------------------------------------------------

    /**
     * Registers a new Member. UC-11.
     *
     * <p>Per II.1.4 / D10a:
     * <ul>
     *   <li>Requires an active Guest session (via {@link RegisterRequestDTO#guestSessionId()}).</li>
     *   <li>The session intentionally remains Guest after register —
     *       {@link #login(LoginRequestDTO)} is the explicit promotion step.</li>
     * </ul>
     */
    public void register(RegisterRequestDTO request) {
        // 1. Cheap format validation first — bots / garbage fail before any IO.
        validateEmail(request.email());
        validatePassword(request.rawPassword());

        // 2. Guest session must exist and still be a Guest.
        Session session = requireActiveGuestSession(request.guestSessionId());

        // 3. Uniqueness.
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }

        // 4. Create user.
        String hashed = passwordHasher.hash(request.rawPassword());
        User user = new User(userRepository.nextId(), request.username(), request.email(), hashed);
        userRepository.save(user);

        // 5. Session stays Guest — just touch the activity timestamp.
        session.touch(clock.instant());
        sessionRepository.save(session);

        log.info("member registered: username={} id={}", user.getUsername(), user.getUserId());
    }

    private static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailFormatException(email);
        }
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new WeakPasswordException("must be at least 8 characters");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new WeakPasswordException("must contain at least one letter and one digit");
        }
    }

    // ------------------------------------------------------------------
    // Login (UC-12) — Guest session promoted in place
    // ------------------------------------------------------------------

    /**
     * Authenticates a Member and promotes their Guest session to a Member
     * session in place. UC-12.
     *
     * <p>"Unknown username" and "wrong password" raise the same exception
     * to prevent username enumeration via the response.
     *
     * @throws GuestSessionRequiredException no / unknown / expired / non-guest sessionId
     * @throws AuthenticationFailedException invalid credentials
     */
    public AuthTokenDTO login(LoginRequestDTO request) {
        // 1. Guest session must exist.
        Session session = requireActiveGuestSession(request.guestSessionId());

        // 2. Authenticate (same exception for both unknown-user and wrong-password).
        User user;
        try {
            user = userRepository.findByUsername(request.username())
                    .orElseThrow(AuthenticationFailedException::new);
            if (!user.verifyPassword(request.rawPassword(), passwordHasher)) {
                throw new AuthenticationFailedException();
            }
        } catch (AuthenticationFailedException e) {
            log.warn("login failed for username={}", request.username());
            throw e;
        }

        // 3. Promote — same sessionId, userId set, expiry bumped to Member TTL.
        Instant memberExpiry = clock.instant().plus(memberTtlMinutes, ChronoUnit.MINUTES);
        session.promoteTo(user.getUserId(), memberExpiry);
        sessionRepository.save(session);

        // 4. Issue a JWT bound to the existing (now-Member) session.
        String token = sessionManager.generateTokenForSession(session, user.getUsername());
        long expiresAt = sessionManager.extractExpiration(token);

        log.info("member logged in: username={} id={} sid={}",
                user.getUsername(), user.getUserId(), session.getSessionId());

        return new AuthTokenDTO(token, expiresAt, user.getUserId(), user.getUsername());
    }

    // ------------------------------------------------------------------
    // Logout (UC-14)
    // ------------------------------------------------------------------

    /**
     * Terminates the authenticated session by deleting the underlying
     * Session row (via {@link ISessionManager#invalidate}). UC-14 / D8 (L1).
     *
     * <p>Per II.3.1 the user is downgraded back to Guest-Visitor. To act
     * again they must call {@link #startGuestSession()} for a fresh
     * sessionId — the old one is dead. (D9a: Member cart persists by userId
     * and is restored on next login — that wiring lives in Phase 4/5.)
     *
     * <p>Idempotent: logout with a {@code null} / blank / already-revoked
     * token is a no-op.
     */
    public void logout(LogoutRequestDTO request) {
        Optional<Integer> userIdOpt = sessionManager.tryExtractUserId(request.token());
        sessionManager.invalidate(request.token());
        userIdOpt.ifPresent(userId -> log.info("member logged out: id={}", userId));
    }

    // Deferred from V1. Not in the UC-12 spec, no acceptance test gates it, and a
    // proper refresh-token flow needs a separate token store + revocation (overlaps
    // with UC-14 logout). Revisit if a later UC requires it.
    public AuthTokenDTO refreshToken(RefreshTokenRequestDTO request) {
        throw new UnsupportedOperationException("refresh-token flow deferred from V1");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Returns the active Guest Session for {@code sessionId}, or throws
     * {@link GuestSessionRequiredException} with a specific reason.
     */
    private Session requireActiveGuestSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new GuestSessionRequiredException(
                    "operation requires an active guest session — call startGuestSession() first");
        }
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new GuestSessionRequiredException("guest session not found"));
        if (session.isMember()) {
            throw new GuestSessionRequiredException("session is not a guest session");
        }
        if (session.isExpiredAt(clock.instant())) {
            throw new GuestSessionRequiredException("guest session expired");
        }
        return session;
    }
}
