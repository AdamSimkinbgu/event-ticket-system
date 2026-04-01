package com.bgu.se.ticketing.application.dto;

/**
 * Response DTO returned after a successful authentication.
 */
public class AuthResponseDTO {

    private String token;
    private UserDTO user;

    public AuthResponseDTO() {
    }

    public AuthResponseDTO(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UserDTO getUser() { return user; }
    public void setUser(UserDTO user) { this.user = user; }
}
