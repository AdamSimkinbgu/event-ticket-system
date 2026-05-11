package com.ticketing.system.Core.Application.dto;

// Input to AuthenticationService.register() (UC-11).
// Raw password — hashed in the application service before reaching the User entity.
public record RegisterRequestDTO(
    String username,
    String email,
    String rawPassword
) {}
