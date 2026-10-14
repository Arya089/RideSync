package com.ridesync.ride.dto;

public record RideStatusResponse(Long rideId, String status, int completedBookings) {}