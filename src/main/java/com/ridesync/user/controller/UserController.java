package com.ridesync.user.controller;

import com.ridesync.common.exception.ApiException;
import com.ridesync.user.dto.ProfileResponse;
import com.ridesync.user.entity.User;
import com.ridesync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/profile")
    public ProfileResponse profile(Authentication authentication) {
        User u = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return new ProfileResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole().name());
    }
}