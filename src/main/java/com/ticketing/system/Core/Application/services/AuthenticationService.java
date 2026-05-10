package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.LogoutRequestDTO;
import com.ticketing.system.Core.Application.dto.RefreshTokenRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;

public class AuthenticationService {

    public int extractUserId(String token) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractUserId'");
    }

    public boolean validateToken(String token) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'validateToken'");
    }

    // UC-11 — register a new Member; session intentionally remains Guest per II.1.4.
    public void register(RegisterRequestDTO request) {
        throw new UnsupportedOperationException("UC-11: not implemented");
    }

    // UC-12 — issue a JWT after credential verification; publishes MemberLoggedIn event
    // (UC-13 + UC-37 listen).
    public AuthTokenDTO login(LoginRequestDTO request) {
        throw new UnsupportedOperationException("UC-12: not implemented");
    }

    // UC-14 — invalidate session, abandon cart per II.3.0.1; publishes MemberLoggedOut event.
    public void logout(LogoutRequestDTO request) {
        throw new UnsupportedOperationException("UC-14: not implemented");
    }

    // UC-12 supplemental — refresh an expiring token without forcing re-login.
    public AuthTokenDTO refreshToken(RefreshTokenRequestDTO request) {
        throw new UnsupportedOperationException("UC-12 (refresh): not implemented");
    }
}
