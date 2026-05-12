package com.ticketing.system.Infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtSessionManager implements ISessionManager {

    private static final String CLAIM_USERNAME = "username";

    private final SecretKey signingKey;
    private final long expirationMillis;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public JwtSessionManager(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60 * 1000;
    }

    @Override
    public String generateToken(int userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        parseClaims(token);
        return true;
    }

    @Override
    public int extractUserId(String token) {
        return Integer.parseInt(parseClaims(token).getSubject());
    }

    @Override
    public String extractUsername(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    @Override
    public long extractExpiration(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    @Override
    public boolean isExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (SessionExpiredException e) {
            return true;
        } catch (InvalidTokenException e) {
            // Malformed token cannot be checked for expiry; treat as unusable.
            return true;
        }
    }

    @Override
    public void invalidate(String token) {
        if (token == null || token.isBlank()) return;
        revokedTokens.add(token);
    }

    @Override
    public boolean isOnline(int userId) {
        throw new UnsupportedOperationException("UC-35/36: not implemented");
    }

    @Override
    public Optional<Integer> tryExtractUserId(String token) {
        try {
            return Optional.of(extractUserId(token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        if (revokedTokens.contains(token)) {
            throw new InvalidTokenException("token has been revoked");
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new SessionExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("token validation failed");
        }
    }
}
