package com.ridesync.booking.service;

import com.ridesync.booking.dto.BookingResponse;
import com.ridesync.booking.entity.Booking;
import com.ridesync.booking.repository.BookingRepository;
import com.ridesync.common.exception.ApiException;
import com.ridesync.ride.entity.Ride;
import com.ridesync.ride.repository.RideRepository;
import com.ridesync.user.entity.User;
import com.ridesync.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class BookingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<Booking.Status> ACTIVE =
            List.of(Booking.Status.PENDING, Booking.Status.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final SeatLockService lockService;
    private final TransactionTemplate tx;

    public BookingService(BookingRepository bookingRepository,
                          RideRepository rideRepository,
                          UserRepository userRepository,
                          SeatLockService lockService,
                          PlatformTransactionManager txManager) {
        this.bookingRepository = bookingRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.lockService = lockService;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Order matters: take the lock, THEN run the transaction, commit, THEN release.
     * If the lock were released before the commit, another request could read stale seat counts.
     * That is why this method is not @Transactional and uses a TransactionTemplate instead.
     */
    public BookingResponse book(String passengerEmail, Long rideId, int seats) {
        String token = lockService.acquire(rideId);
        if (token == null) {
            throw new ApiException(HttpStatus.CONFLICT, "This ride is busy, please retry");
        }
        try {
            return tx.execute(status -> doBook(passengerEmail, rideId, seats));
        } finally {
            lockService.release(rideId, token);
        }
    }

    public BookingResponse cancel(String passengerEmail, Long bookingId) {
        Long rideId = tx.execute(status -> bookingRepository.findById(bookingId)
                .map(b -> b.getRide().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found")));

        String token = lockService.acquire(rideId);
        if (token == null) {
            throw new ApiException(HttpStatus.CONFLICT, "This ride is busy, please retry");
        }
        try {
            return tx.execute(status -> doCancel(passengerEmail, bookingId));
        } finally {
            lockService.release(rideId, token);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(String passengerEmail) {
        User passenger = userRepository.findByEmail(passengerEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        return bookingRepository.findByPassengerIdOrderByCreatedAtDesc(passenger.getId())
                .stream().map(this::toResponse).toList();
    }

    private BookingResponse doBook(String email, Long rideId, int seats) {
        User passenger = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getStatus() != Ride.Status.SCHEDULED || !ride.getDepartureTime().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This ride is not open for booking");
        }
        if (bookingRepository.existsByRideIdAndPassengerIdAndStatusIn(rideId, passenger.getId(), ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a booking on this ride");
        }

        // Second line of defence: the database refuses to go below zero seats.
        int updated = rideRepository.reserveSeats(rideId, seats, Ride.Status.SCHEDULED);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough seats available");
        }

        Booking booking = Booking.builder()
                .ride(ride)
                .passenger(passenger)
                .seats(seats)
                .totalPrice(ride.getPricePerSeat().multiply(BigDecimal.valueOf(seats)))
                .status(Booking.Status.PENDING)
                .build();
        return toResponse(bookingRepository.save(booking));
    }

    private BookingResponse doCancel(String email, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .filter(b -> b.getPassenger().getEmail().equals(email))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!ACTIVE.contains(booking.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking is already " + booking.getStatus());
        }
        booking.setStatus(Booking.Status.CANCELLED);
        rideRepository.releaseSeats(booking.getRide().getId(), booking.getSeats());
        return toResponse(booking);
    }

    private BookingResponse toResponse(Booking b) {
        Ride r = b.getRide();
        return new BookingResponse(
                b.getId(),
                r.getId(),
                r.getOriginName(),
                r.getDestinationName(),
                LocalDateTime.ofInstant(r.getDepartureTime(), ZONE),
                b.getSeats(),
                b.getTotalPrice(),
                b.getStatus().name());
    }
}