package com.ridesync.ride.repository;

import com.ridesync.ride.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
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
}