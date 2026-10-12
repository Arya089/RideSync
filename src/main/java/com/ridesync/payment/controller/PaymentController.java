package com.ridesync.payment.controller;

import com.ridesync.payment.dto.InitiatePaymentRequest;
import com.ridesync.payment.dto.InitiatePaymentResponse;
import com.ridesync.payment.dto.PaymentResponse;
import com.ridesync.payment.dto.VerifyPaymentRequest;
import com.ridesync.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('PASSENGER')")
    public InitiatePaymentResponse initiate(@Valid @RequestBody InitiatePaymentRequest req,
                                            Authentication authentication) {
        return paymentService.initiate(authentication.getName(), req.bookingId());
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('PASSENGER')")
    public PaymentResponse verify(@Valid @RequestBody VerifyPaymentRequest req,
                                  Authentication authentication) {
        return paymentService.verify(authentication.getName(), req);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('PASSENGER')")
    public List<PaymentResponse> history(Authentication authentication) {
        return paymentService.history(authentication.getName());
    }

    // Public endpoint: authenticity comes from the HMAC signature, not a JWT.
    // We take the RAW body because the signature is computed over the exact bytes Razorpay sent.
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}