package com.ridesync.ride.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PostRideRequest(
        @NotBlank String originName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double originLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double originLng,
        @NotBlank String destinationName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double destLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double destLng,
        @NotNull @Future LocalDateTime departureTime,
        @Min(1) @Max(8) int totalSeats,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal pricePerSeat
) {}