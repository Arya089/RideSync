package com.ridesync.payment.dto;

import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(@NotNull Long bookingId) {}