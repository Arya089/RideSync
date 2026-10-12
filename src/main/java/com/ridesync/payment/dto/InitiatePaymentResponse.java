package com.ridesync.payment.dto;

public record InitiatePaymentResponse(
        String orderId,
        long amountPaise,
        String currency,
        String keyId,
        Long bookingId
) {}