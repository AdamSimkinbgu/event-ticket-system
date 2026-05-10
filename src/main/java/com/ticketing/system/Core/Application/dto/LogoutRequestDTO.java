package com.ticketing.system.Core.Application.dto;

// Input to AuthenticationService.logout() (UC-14).
// 'token' identifies the session to invalidate. 'releaseCart' lets the caller
// override the default behavior (the design walkthrough's open question on cart-on-logout).
public record LogoutRequestDTO(
    String token,
    boolean releaseCart
) {}
