package com.ticketing.system.Core.Application.services;

import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.LogoutRequestDTO;
import com.ticketing.system.Core.Application.dto.RefreshTokenRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateEmailException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.InvalidEmailFormatException;
import com.ticketing.system.Core.Domain.exceptions.WeakPasswordException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

/**
 * Application service for the auth slice — registration (UC-11), login (UC-12),
 * and logout (UC-14).
 *
 * <p>Orchestrates the User repository, password hasher, and session manager.
 * Cross-service effects (e.g. UC-13 order restoration on login, UC-37
 * notification flush) are integrated via direct service calls when those UCs
 * are implemented.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final IUserRepository userRepository;
    private final IPasswordHasher passwordHasher;
    private final ISessionManager sessionManager;

    public AuthenticationService(
            IUserRepository userRepository,
            IPasswordHasher passwordHasher,
            ISessionManager sessionManager) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
    }

    /** Delegates to {@link ISessionManager#extractUserId}. */
    public int extractUserId(String token) {
        return sessionManager.extractUserId(token);
    }

    /** Delegates to {@link ISessionManager#validateToken}. */
    public boolean validateToken(String token) {
        return sessionManager.validateToken(token);
    }

    /**
     * Registers a new Member. UC-11.
     *
     * <p>Session intentionally remains Guest per II.1.4 — caller must explicitly
     * log in to upgrade to Member-Visitor.
     *
     * @throws InvalidEmailFormatException email fails format check
     * @throws WeakPasswordException password fails strength rules
     * @throws DuplicateUsernameException username already taken
     * @throws DuplicateEmailException email already registered
     */
    public void register(RegisterRequestDTO request) {
        validateEmail(request.email());
        validatePassword(request.rawPassword());

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }

        String hashed = passwordHasher.hash(request.rawPassword());
        User user = new User(userRepository.nextId(), request.username(), request.email(), hashed);
        userRepository.save(user);

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

    /**
     * Authenticates a Member and issues a JWT. UC-12.
     *
     * <p>"Unknown username" and "wrong password" raise the same exception to
     * avoid username enumeration via the response.
     *
     * @throws AuthenticationFailedException invalid credentials
     */
    public AuthTokenDTO login(LoginRequestDTO request) {
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

        String token = sessionManager.generateToken(user.getUserId(), user.getUsername());
        long expiresAt = sessionManager.extractExpiration(token);

        log.info("member logged in: username={} id={}", user.getUsername(), user.getUserId());

        return new AuthTokenDTO(token, expiresAt, user.getUserId(), user.getUsername());
    }

    /**
     * Terminates the authenticated session by revoking the token. UC-14.
     *
     * <p>Per II.3.1 the session state downgrades back to Guest-Visitor. Cart
     * state is not touched here (II.3.0.1 / II.3.0.3 govern Active Orders
     * separately). Idempotent: logout with a {@code null} / blank /
     * already-revoked token is a no-op.
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
}
