package com.ticketing.system.Infrastructure.security;

import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;

// Implements IPasswordHasher using Spring Security's BCryptPasswordEncoder.
// Lecture 2 recommends BCrypt for password hashing.
// All bodies are stubs — V1 implementation is owned by the team member assigned to UC-11.
public class BcryptPasswordHasher implements IPasswordHasher {

    @Override
    public String hash(String rawPassword) {
        throw new UnsupportedOperationException("UC-11: wire BCryptPasswordEncoder.encode here");
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        throw new UnsupportedOperationException("UC-12: wire BCryptPasswordEncoder.matches here");
    }
}
