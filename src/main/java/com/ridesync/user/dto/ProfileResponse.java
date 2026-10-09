package com.ridesync.user.dto;

public record ProfileResponse(Long id, String email, String fullName, String phone, String role) {}