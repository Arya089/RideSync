package com.ridesync.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BookRequest(
        @Min(1) @Max(8) int seats
) {}