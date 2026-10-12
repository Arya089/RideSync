package com.ridesync.payment.repository;

import com.ridesync.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findFirstByBookingIdAndStatus(Long bookingId, Payment.Status status);

    List<Payment> findByBookingPassengerEmailOrderByCreatedAtDesc(String email);
}