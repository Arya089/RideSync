package com.ridesync.tracking.service;

import com.ridesync.booking.entity.Booking;
import com.ridesync.booking.repository.BookingRepository;
import com.ridesync.ride.repository.RideRepository;
import com.ridesync.user.entity.User;
import com.ridesync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingAccessService {

    public enum Role { DRIVER, VIEWER, NONE }

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public static String locationKey(Long rideId) {
        return "driver:location:" + rideId;
    }

    /** The ride's driver may send; passengers with a CONFIRMED booking may watch; nobody else. */
    @Transactional(readOnly = true)
    public Role roleFor(String email, Long rideId) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !rideRepository.existsById(rideId)) {
            return Role.NONE;
        }
        if (rideRepository.existsByIdAndDriverId(rideId, user.getId())) {
            return Role.DRIVER;
        }
        if (bookingRepository.existsByRideIdAndPassengerIdAndStatusIn(
                rideId, user.getId(), List.of(Booking.Status.CONFIRMED))) {
            return Role.VIEWER;
        }
        return Role.NONE;
    }
}