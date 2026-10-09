package com.ridesync.booking.repository;

import com.ridesync.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    boolean existsByRideIdAndPassengerIdAndStatusIn(Long rideId, Long passengerId,
                                                    Collection<Booking.Status> statuses);
}