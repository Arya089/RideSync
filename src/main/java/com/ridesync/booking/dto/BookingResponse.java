package com.ridesync.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long rideId,
        String originName,
        String destinationName,
        LocalDateTime departureTime,
        int seats,
        BigDecimal totalPrice,
        String status
) {}