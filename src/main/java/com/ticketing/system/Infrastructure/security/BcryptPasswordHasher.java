package com.ticketing.system.Infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;

@Component
public class BcryptPasswordHasher implements IPasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        return encoder.matches(rawPassword, storedHash);
    }
}
