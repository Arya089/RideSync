package com.ridesync.ride.service;

import com.ridesync.common.exception.ApiException;
import com.ridesync.ride.dto.PostRideRequest;
import com.ridesync.ride.dto.RideResponse;
import com.ridesync.ride.entity.Ride;
import com.ridesync.ride.repository.RideRepository;
import com.ridesync.user.entity.User;
import com.ridesync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    @Transactional
    public RideResponse postRide(String driverEmail, PostRideRequest req) {
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        Ride ride = Ride.builder()
                .driver(driver)
                .originName(req.originName().trim())
                .destinationName(req.destinationName().trim())
                .originPoint(point(req.originLat(), req.originLng()))
                .destinationPoint(point(req.destLat(), req.destLng()))
                .departureTime(req.departureTime().atZone(ZONE).toInstant())
                .totalSeats(req.totalSeats())
                .availableSeats(req.totalSeats())
                .pricePerSeat(req.pricePerSeat())
                .status(Ride.Status.SCHEDULED)
                .build();
        return toResponse(rideRepository.save(ride));
    }

    @Transactional(readOnly = true)
    public List<RideResponse> search(double originLat, double originLng,
                                     double destLat, double destLng,
                                     LocalDate date, int seats, double radiusKm) {
        if (seats < 1 || seats > 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "seats must be between 1 and 8");
        }
        if (radiusKm < 1 || radiusKm > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "radiusKm must be between 1 and 50");
        }
        Instant now = Instant.now();
        Instant from = date.atStartOfDay(ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        if (from.isBefore(now)) {
            from = now;
        }
        return rideRepository
                .search(originLat, originLng, destLat, destLng, seats, from, to, radiusKm * 1000)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RideResponse getById(Long id) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
        return toResponse(ride);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> myRides(String driverEmail) {
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        return rideRepository.findByDriverIdOrderByDepartureTimeDesc(driver.getId())
                .stream().map(this::toResponse).toList();
    }

    // x = longitude, y = latitude. Easy to get backwards.
    private Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private RideResponse toResponse(Ride r) {
        return new RideResponse(
                r.getId(),
                r.getDriver().getId(),
                r.getDriver().getFullName(),
                r.getOriginName(),
                r.getOriginPoint().getY(),
                r.getOriginPoint().getX(),
                r.getDestinationName(),
                r.getDestinationPoint().getY(),
                r.getDestinationPoint().getX(),
                LocalDateTime.ofInstant(r.getDepartureTime(), ZONE),
                r.getTotalSeats(),
                r.getAvailableSeats(),
                r.getPricePerSeat(),
                r.getStatus().name());
    }
}