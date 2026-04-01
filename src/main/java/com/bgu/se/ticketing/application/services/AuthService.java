package com.bgu.se.ticketing.application.services;

import com.bgu.se.ticketing.application.dto.AuthResponseDTO;
import com.bgu.se.ticketing.application.dto.LoginRequestDTO;
import com.bgu.se.ticketing.application.dto.RegisterRequestDTO;
import com.bgu.se.ticketing.application.dto.UserDTO;
import com.bgu.se.ticketing.domain.models.User;
import com.bgu.se.ticketing.domain.models.UserRole;
import com.bgu.se.ticketing.domain.repositories.IUserRepository;
import com.bgu.se.ticketing.infrastructure.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Application service (use-case) for authentication and user management.
 *
 * <p>Coordinates the domain and infrastructure layers. Contains no business logic –
 * orchestration only.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(IUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers a new user with the BUYER role.
     *
     * @throws IllegalArgumentException if the username or email is already taken
     */
    public UserDTO register(RegisterRequestDTO request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = User.create(request.getUsername(), request.getEmail(), passwordHash, UserRole.BUYER);
        User saved = userRepository.save(user);

        log.info("Registered new user: {}", saved.getUsername());
        return toDTO(saved);
    }

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @throws NoSuchElementException   if the username does not exist
     * @throws IllegalArgumentException if the password is incorrect
     */
    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + request.getUsername()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        log.info("User logged in: {}", user.getUsername());
        return new AuthResponseDTO(token, toDTO(user));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
