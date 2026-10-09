package com.ridesync.auth.service;

import com.ridesync.auth.dto.AuthResponse;
import com.ridesync.auth.dto.LoginRequest;
import com.ridesync.auth.dto.RegisterRequest;
import com.ridesync.auth.security.JwtService;
import com.ridesync.common.exception.ApiException;
import com.ridesync.user.entity.User;
import com.ridesync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.role() == User.Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role must be DRIVER or PASSENGER");
        }
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .phone(req.phone())
                .role(req.role())
                .build();
        userRepository.save(user);
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}