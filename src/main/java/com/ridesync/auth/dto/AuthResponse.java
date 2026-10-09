package com.ridesync.auth.dto;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String email,
        String fullName,
        String role
) {}