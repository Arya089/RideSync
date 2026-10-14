package com.ridesync.ride.service;

import com.ridesync.booking.entity.Booking;
import com.ridesync.booking.repository.BookingRepository;
import com.ridesync.common.exception.ApiException;
import com.ridesync.ride.dto.RideStatusResponse;
import com.ridesync.ride.entity.Ride;
import com.ridesync.ride.repository.RideRepository;
import com.ridesync.user.entity.User;
import com.ridesync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RideLifecycleService {

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public RideStatusResponse start(String driverEmail, Long rideId) {
        User driver = findDriver(driverEmail);
        requireOwnRide(rideId, driver.getId());
        int changed = rideRepository.transitionStatus(
                rideId, driver.getId(), Ride.Status.SCHEDULED, Ride.Status.STARTED);
        if (changed == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a SCHEDULED ride can be started");
        }
        return new RideStatusResponse(rideId, Ride.Status.STARTED.name(), 0);
    }

    @Transactional
    public RideStatusResponse complete(String driverEmail, Long rideId) {
        User driver = findDriver(driverEmail);
        requireOwnRide(rideId, driver.getId());
        int changed = rideRepository.transitionStatus(
                rideId, driver.getId(), Ride.Status.STARTED, Ride.Status.COMPLETED);
        if (changed == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a STARTED ride can be completed");
        }
        int done = bookingRepository.transitionAllForRide(
                rideId, Booking.Status.CONFIRMED, Booking.Status.COMPLETED);
        return new RideStatusResponse(rideId, Ride.Status.COMPLETED.name(), done);
    }

    private User findDriver(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void requireOwnRide(Long rideId, Long driverId) {
        if (!rideRepository.existsById(rideId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Ride not found");
        }
        if (!rideRepository.existsByIdAndDriverId(rideId, driverId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This is not your ride");
        }
    }
}