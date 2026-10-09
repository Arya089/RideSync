package com.ridesync.ride.controller;

import com.ridesync.ride.dto.PostRideRequest;
import com.ridesync.ride.dto.RideResponse;
import com.ridesync.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    @PostMapping("/post")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> post(@Valid @RequestBody PostRideRequest req,
                                             Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rideService.postRide(authentication.getName(), req));
    }

    @GetMapping("/search")
    public List<RideResponse> search(@RequestParam double originLat,
                                     @RequestParam double originLng,
                                     @RequestParam double destLat,
                                     @RequestParam double destLng,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(defaultValue = "1") int seats,
                                     @RequestParam(defaultValue = "10") double radiusKm) {
        return rideService.search(originLat, originLng, destLat, destLng, date, seats, radiusKm);
    }

    @GetMapping("/my-rides")
    @PreAuthorize("hasRole('DRIVER')")
    public List<RideResponse> myRides(Authentication authentication) {
        return rideService.myRides(authentication.getName());
    }

    @GetMapping("/{id}")
    public RideResponse getById(@PathVariable Long id) {
        return rideService.getById(id);
    }
}