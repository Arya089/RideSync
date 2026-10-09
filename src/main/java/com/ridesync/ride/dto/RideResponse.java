package com.ridesync.ride.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideResponse(
        Long id,
        Long driverId,
        String driverName,
        String originName,
        double originLat,
        double originLng,
        String destinationName,
        double destLat,
        double destLng,
        LocalDateTime departureTime,
        int totalSeats,
        int availableSeats,
        BigDecimal pricePerSeat,
        String status
) {}