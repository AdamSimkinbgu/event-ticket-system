package com.ticketing.system.Core.Application.dto;

// Input to AuthenticationService.logout() (UC-14). The token identifies the session
// to invalidate. UC-14 per II.3.1 is purely a session-termination concern; cart state
// is governed by separate rules (II.3.0.1, II.3.0.3) and is not touched here.
public record LogoutRequestDTO(
    String token
) {}
