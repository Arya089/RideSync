package com.ridesync.booking.service;

import com.ridesync.booking.entity.Booking;
import com.ridesync.booking.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class BookingExpiryJob {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final long expiryMinutes;

    public BookingExpiryJob(BookingRepository bookingRepository,
                            BookingService bookingService,
                            @Value("${app.booking.pending-expiry-minutes}") long expiryMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.expiryMinutes = expiryMinutes;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expirePendingBookings() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(expiryMinutes));
        List<Booking> stale = bookingRepository.findByStatusAndCreatedAtBefore(Booking.Status.PENDING, cutoff);
        for (Booking b : stale) {
            bookingService.expire(b.getId());
        }
        if (!stale.isEmpty()) {
            log.info("Checked {} stale pending booking(s) for expiry", stale.size());
        }
    }
}