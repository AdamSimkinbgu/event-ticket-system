package com.ticketing.system.Core.Application.dto;

// Input to AuthenticationService.login() (UC-12).
public record LoginRequestDTO(
    String username,
    String rawPassword
) {}
