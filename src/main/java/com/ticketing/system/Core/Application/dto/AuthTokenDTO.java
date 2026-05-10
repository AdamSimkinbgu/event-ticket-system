package com.ticketing.system.Core.Application.dto;

// Returned by AuthenticationService.login() (UC-12).
// 'expiresAt' is epoch millis to keep the DTO primitive-only.
public record AuthTokenDTO(
    String token,
    long expiresAtEpochMillis,
    int userId,
    String username
) {}
