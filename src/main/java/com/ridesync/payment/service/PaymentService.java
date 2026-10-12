package com.ridesync.payment.service;

import com.ridesync.booking.entity.Booking;
import com.ridesync.booking.repository.BookingRepository;
import com.ridesync.common.exception.ApiException;
import com.ridesync.payment.dto.InitiatePaymentResponse;
import com.ridesync.payment.dto.PaymentResponse;
import com.ridesync.payment.dto.VerifyPaymentRequest;
import com.ridesync.payment.entity.Payment;
import com.ridesync.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PaymentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;
    private final String keySecret;
    private final String webhookSecret;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          PaymentGateway gateway,
                          PlatformTransactionManager txManager,
                          @Value("${app.payment.key-secret}") String keySecret,
                          @Value("${app.payment.webhook-secret}") String webhookSecret) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(txManager);
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    private record InitContext(BigDecimal amount, String existingOrderId) {}

    /**
     * Not @Transactional on purpose: the slow HTTP call to the gateway must not hold a
     * database connection. We validate in one short transaction, call out, then save in another.
     */
    public InitiatePaymentResponse initiate(String email, Long bookingId) {
        InitContext ctx = tx.execute(status -> {
            Booking booking = bookingRepository.findById(bookingId)
                    .filter(b -> b.getPassenger().getEmail().equals(email))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
            if (booking.getStatus() != Booking.Status.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Booking is " + booking.getStatus() + ", payment is not possible");
            }
            String existing = paymentRepository
                    .findFirstByBookingIdAndStatus(bookingId, Payment.Status.CREATED)
                    .map(Payment::getRazorpayOrderId)
                    .orElse(null);
            return new InitContext(booking.getTotalPrice(), existing);
        });

        long paise = ctx.amount().movePointRight(2).longValueExact();
        String orderId = ctx.existingOrderId();

        if (orderId == null) {
            String created = gateway.createOrder(paise, "booking_" + bookingId);
            tx.executeWithoutResult(status -> paymentRepository.save(Payment.builder()
                    .booking(bookingRepository.getReferenceById(bookingId))
                    .razorpayOrderId(created)
                    .amount(ctx.amount())
                    .currency("INR")
                    .status(Payment.Status.CREATED)
                    .build()));
            orderId = created;
        }
        return new InitiatePaymentResponse(orderId, paise, "INR", gateway.keyId(), bookingId);
    }

    /** Called by the client after Razorpay Checkout succeeds. */
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentResponse verify(String email, VerifyPaymentRequest req) {
        Payment payment = paymentRepository.findByRazorpayOrderId(req.razorpayOrderId())
                .filter(p -> p.getBooking().getPassenger().getEmail().equals(email))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment order not found"));

        String expected = Signatures.hmacSha256Hex(keySecret,
                req.razorpayOrderId() + "|" + req.razorpayPaymentId());
        if (!Signatures.safeEquals(expected, req.razorpaySignature())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
        }
        if (!markPaid(payment, req.razorpayPaymentId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Booking expired before payment completed; a refund will be issued");
        }
        return toResponse(payment);
    }

    /** Server-to-server confirmation from Razorpay. This is the source of truth. */
    @Transactional
    @SuppressWarnings("unchecked")
    public void handleWebhook(String rawBody, String signature) {
        String expected = Signatures.hmacSha256Hex(webhookSecret, rawBody);
        if (!Signatures.safeEquals(expected, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(rawBody);
        String event = String.valueOf(root.get("event"));

        Object payload = root.get("payload");
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return;
        }
        Object paymentWrapper = payloadMap.get("payment");
        if (!(paymentWrapper instanceof Map<?, ?> wrapperMap)) {
            return;
        }
        Object entityObj = wrapperMap.get("entity");
        if (!(entityObj instanceof Map<?, ?> entityRaw)) {
            return;
        }
        Map<String, Object> entity = (Map<String, Object>) entityRaw;
        String orderId = (String) entity.get("order_id");
        String paymentId = (String) entity.get("id");
        if (orderId == null) {
            return;
        }

        Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown order {}", orderId);
            return;
        }

        switch (event) {
            case "payment.captured", "order.paid" -> markPaid(payment, paymentId);
            default -> log.info("Ignoring webhook event {}", event);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> history(String email) {
        return paymentRepository.findByBookingPassengerEmailOrderByCreatedAtDesc(email)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Idempotent: the browser callback and the webhook can both arrive, in any order, any number of times.
     * Returns true if the booking is confirmed (now or earlier), false if it had already expired.
     */
    private boolean markPaid(Payment payment, String paymentId) {
        if (payment.getStatus() == Payment.Status.PAID) {
            return true;
        }
        Long bookingId = payment.getBooking().getId();
        boolean confirmed =
                bookingRepository.transition(bookingId, Booking.Status.PENDING, Booking.Status.CONFIRMED) == 1
                        || bookingRepository.statusOf(bookingId) == Booking.Status.CONFIRMED;

        payment.setRazorpayPaymentId(paymentId);
        payment.setStatus(confirmed ? Payment.Status.PAID : Payment.Status.REFUND_REQUIRED);
        paymentRepository.save(payment);
        return confirmed;
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getBooking().getId(),
                p.getRazorpayOrderId(),
                p.getRazorpayPaymentId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                LocalDateTime.ofInstant(p.getCreatedAt(), ZONE));
    }
}