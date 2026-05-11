package com.ticketing.system.unit.nfr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;
import com.ticketing.system.Infrastructure.security.BcryptPasswordHasher;
import com.ticketing.system.Infrastructure.security.JwtSessionManager;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Consolidated SLR.2 audit for the auth slice (UC-11 + UC-12). Each test maps to a row
// in docs/slr2-audit.md.
@SpringBootTest
@ActiveProfiles("test")
class Slr2DataSecurityTest {

    private static final String RAW_PW = "AuditPassword1";

    @Autowired private AuthenticationService authService;
    @Autowired private BcryptPasswordHasher hasher;
    @Value("${jwt.secret}") private String jwtSecret;

    @Test
    void authTokenDto_doesNotExposePassword() {
        for (RecordComponent rc : AuthTokenDTO.class.getRecordComponents()) {
            assertFalse(rc.getName().toLowerCase().contains("password"),
                "AuthTokenDTO must not expose any password-named component, found: " + rc.getName());
        }
    }

    @Test
    void passwordIsNotLoggedDuringRegisterOrLogin() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            authService.register(new RegisterRequestDTO("audit-log-user", "audit-log@example.com", RAW_PW));
            authService.login(new LoginRequestDTO("audit-log-user", RAW_PW));

            List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertTrue(messages.stream().noneMatch(m -> m != null && m.contains(RAW_PW)),
                "raw password leaked into a log message");
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void storedPasswordIsBcryptFormat() {
        // BCrypt outputs start with a versioned algorithm identifier — proves storage is hashed,
        // not plaintext or some other reversible encoding.
        String stored = hasher.hash(RAW_PW);
        assertTrue(stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"),
            "stored value is not BCrypt format: " + stored);
    }

    @Test
    void bcryptSaltsArePerHash() {
        // Defends against rainbow-table attacks: same input must hash to different outputs.
        String a = hasher.hash(RAW_PW);
        String b = hasher.hash(RAW_PW);
        assertNotEquals(a, b);
    }

    @Test
    void jwtWithBadSignatureIsRejected() {
        authService.register(new RegisterRequestDTO("audit-sig-user", "audit-sig@example.com", RAW_PW));
        AuthTokenDTO issued = authService.login(new LoginRequestDTO("audit-sig-user", RAW_PW));

        String tampered = issued.token() + "AAAA";
        assertThrows(InvalidTokenException.class, () -> authService.validateToken(tampered));
    }

    @Test
    void expiredJwtIsRejected() {
        // Standalone manager with negative expiry so we don't have to wait for a real one.
        JwtSessionManager pastDue = new JwtSessionManager(jwtSecret, -1);
        String alreadyExpired = pastDue.generateToken(1, "audit");
        assertThrows(SessionExpiredException.class, () -> pastDue.validateToken(alreadyExpired));
    }

    @Test
    void authFailureMessage_doesNotEnumerateUsernames() {
        authService.register(new RegisterRequestDTO("audit-enum-user", "audit-enum@example.com", RAW_PW));

        AuthenticationFailedException wrongPw = assertThrows(AuthenticationFailedException.class, () ->
            authService.login(new LoginRequestDTO("audit-enum-user", "WrongPassword1"))
        );
        AuthenticationFailedException noSuchUser = assertThrows(AuthenticationFailedException.class, () ->
            authService.login(new LoginRequestDTO("definitely-not-registered", "Anything1"))
        );

        // Identical exception type and identical message — no information about which check failed.
        assertEquals(wrongPw.getClass(), noSuchUser.getClass());
        assertEquals(wrongPw.getMessage(), noSuchUser.getMessage());
    }
}
