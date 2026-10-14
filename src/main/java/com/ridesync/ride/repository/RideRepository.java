package com.ridesync.ride.repository;

import com.ridesync.ride.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByDriverIdOrderByDepartureTimeDesc(Long driverId);

    @Query(value = """
            SELECT r.* FROM rides r
            WHERE r.status = 'SCHEDULED'
              AND r.available_seats >= :seats
              AND r.departure_time >= :fromTime
              AND r.departure_time < :toTime
              AND ST_DWithin(CAST(r.origin_point AS geography),
                             CAST(ST_SetSRID(ST_MakePoint(:originLng, :originLat), 4326) AS geography),
                             :radiusMeters)
              AND ST_DWithin(CAST(r.destination_point AS geography),
                             CAST(ST_SetSRID(ST_MakePoint(:destLng, :destLat), 4326) AS geography),
                             :radiusMeters)
            ORDER BY r.departure_time
            """, nativeQuery = true)
    List<Ride> search(@Param("originLat") double originLat,
                      @Param("originLng") double originLng,
                      @Param("destLat") double destLat,
                      @Param("destLng") double destLng,
                      @Param("seats") int seats,
                      @Param("fromTime") Instant fromTime,
                      @Param("toTime") Instant toTime,
                      @Param("radiusMeters") double radiusMeters);

    // Atomic seat reservation: the WHERE clause makes it impossible to go below zero,
    // even if the Redis lock were to fail. Returns 0 rows if not enough seats.
    @Modifying
    @Query("update Ride r set r.availableSeats = r.availableSeats - :seats "
            + "where r.id = :id and r.availableSeats >= :seats and r.status = :status")
    int reserveSeats(@Param("id") Long id, @Param("seats") int seats,
                     @Param("status") Ride.Status status);

    @Modifying
    @Query("update Ride r set r.availableSeats = r.availableSeats + :seats "
            + "where r.id = :id and r.availableSeats + :seats <= r.totalSeats")
    int releaseSeats(@Param("id") Long id, @Param("seats") int seats);
    boolean existsByIdAndDriverId(Long id, Long driverId);

    boolean existsByIdAndStatus(Long id, Ride.Status status);

    // Atomic status change, only for the owning driver and only from the expected status.
    @Modifying
    @Query("update Ride r set r.status = :toStatus "
            + "where r.id = :id and r.driver.id = :driverId and r.status = :fromStatus")
    int transitionStatus(@Param("id") Long id,
                         @Param("driverId") Long driverId,
                         @Param("fromStatus") Ride.Status fromStatus,
                         @Param("toStatus") Ride.Status toStatus);
}
