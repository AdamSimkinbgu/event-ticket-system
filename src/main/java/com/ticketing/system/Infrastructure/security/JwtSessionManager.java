package com.ticketing.system.Infrastructure.security;

import java.util.Optional;

import com.ticketing.system.Core.Application.interfaces.ISessionManager;

// Implements ISessionManager using io.jsonwebtoken (jjwt).
// Lecture 2's TokenService example used this library.
// All bodies are stubs — V1 implementation is owned by the team member assigned to UC-12.
// Wire io.jsonwebtoken.Jwts builder/parser; read jwt.secret + jwt.expiration-minutes
// from application.yml via @Value or constructor injection.
public class JwtSessionManager implements ISessionManager {

    @Override
    public String generateToken(int userId, String username) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }

    @Override
    public boolean validateToken(String token) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }

    @Override
    public int extractUserId(String token) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }

    @Override
    public String extractUsername(String token) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }

    @Override
    public boolean isExpired(String token) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }

    @Override
    public void invalidate(String token) {
        throw new UnsupportedOperationException("UC-14: not implemented");
    }

    @Override
    public boolean isOnline(int userId) {
        throw new UnsupportedOperationException("UC-35/36: not implemented");
    }

    @Override
    public Optional<Integer> tryExtractUserId(String token) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }
}
