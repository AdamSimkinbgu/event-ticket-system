package com.ticketing.system.Core.Application.services;

import java.util.regex.Pattern;

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

@Service
public class AuthenticationService {

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

    public int extractUserId(String token) {
        return sessionManager.extractUserId(token);
    }

    public boolean validateToken(String token) {
        return sessionManager.validateToken(token);
    }

    // UC-11 — register a new Member; session intentionally remains Guest per II.1.4.
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

    // UC-12 — issue a JWT after credential verification.
    // Domain-event publication (MemberLoggedIn for UC-13 / UC-37 consumers) is deferred;
    // see docs/plan-domain-events.md.
    public AuthTokenDTO login(LoginRequestDTO request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(AuthenticationFailedException::new);

        if (!user.verifyPassword(request.rawPassword(), passwordHasher)) {
            throw new AuthenticationFailedException();
        }

        String token = sessionManager.generateToken(user.getUserId(), user.getUsername());
        long expiresAt = sessionManager.extractExpiration(token);
        return new AuthTokenDTO(token, expiresAt, user.getUserId(), user.getUsername());
    }

    // UC-14 — terminate the authenticated session by revoking the token; per II.3.1 the
    // session state downgrades back to Guest-Visitor. Cart state is not touched here
    // (II.3.0.1 / II.3.0.3 govern Active Orders separately). Idempotent: logout with a
    // null / blank / already-revoked token is a no-op.
    public void logout(LogoutRequestDTO request) {
        sessionManager.invalidate(request.token());
    }

    // Deferred from V1. Not in the UC-12 spec, no acceptance test gates it, and a
    // proper refresh-token flow needs a separate token store + revocation (overlaps
    // with UC-14 logout). Revisit if a later UC requires it.
    public AuthTokenDTO refreshToken(RefreshTokenRequestDTO request) {
        throw new UnsupportedOperationException("refresh-token flow deferred from V1");
    }
}
