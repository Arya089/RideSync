package com.ridesync.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long bookingId,
        String orderId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime createdAt
) {}