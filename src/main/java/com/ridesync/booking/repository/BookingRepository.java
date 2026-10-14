package com.ridesync.booking.repository;

import com.ridesync.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    boolean existsByRideIdAndPassengerIdAndStatusIn(Long rideId, Long passengerId,
                                                    Collection<Booking.Status> statuses);

    List<Booking> findByStatusAndCreatedAtBefore(Booking.Status status, Instant cutoff);

    // Atomic compare-and-set: returns 1 only if the booking was in fromStatus.
    // Whoever flips PENDING first (payment vs expiry job) wins; the loser gets 0.
    @Modifying
    @Query("update Booking b set b.status = :toStatus where b.id = :id and b.status = :fromStatus")
    int transition(@Param("id") Long id,
                   @Param("fromStatus") Booking.Status fromStatus,
                   @Param("toStatus") Booking.Status toStatus);

    // Scalar query, so it always reads the database and never a stale cached entity.
    @Query("select b.status from Booking b where b.id = :id")
    Booking.Status statusOf(@Param("id") Long id);
    @Modifying
    @Query("update Booking b set b.status = :toStatus "
            + "where b.ride.id = :rideId and b.status = :fromStatus")
    int transitionAllForRide(@Param("rideId") Long rideId,
                             @Param("fromStatus") Booking.Status fromStatus,
                             @Param("toStatus") Booking.Status toStatus);
}