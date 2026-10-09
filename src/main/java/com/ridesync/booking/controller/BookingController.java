package com.ridesync.booking.controller;

import com.ridesync.booking.dto.BookRequest;
import com.ridesync.booking.dto.BookingResponse;
import com.ridesync.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/rides/{id}/book")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<BookingResponse> book(@PathVariable Long id,
                                                @Valid @RequestBody BookRequest req,
                                                Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.book(authentication.getName(), id, req.seats()));
    }

    @GetMapping("/rides/my-bookings")
    @PreAuthorize("hasRole('PASSENGER')")
    public List<BookingResponse> myBookings(Authentication authentication) {
        return bookingService.myBookings(authentication.getName());
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasRole('PASSENGER')")
    public BookingResponse cancel(@PathVariable Long id, Authentication authentication) {
        return bookingService.cancel(authentication.getName(), id);
    }
}