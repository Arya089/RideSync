package com.ridesync.ride.controller;

import com.ridesync.ride.dto.RideStatusResponse;
import com.ridesync.ride.service.RideLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideLifecycleController {

    private final RideLifecycleService lifecycleService;

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public RideStatusResponse start(@PathVariable Long id, Authentication authentication) {
        return lifecycleService.start(authentication.getName(), id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public RideStatusResponse complete(@PathVariable Long id, Authentication authentication) {
        return lifecycleService.complete(authentication.getName(), id);
    }
}