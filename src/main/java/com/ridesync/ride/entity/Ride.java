package com.ridesync.ride.entity;

import com.ridesync.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id")
    private User driver;

    @Column(nullable = false)
    private String originName;

    @Column(nullable = false)
    private String destinationName;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point originPoint;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point destinationPoint;

    @Column(nullable = false)
    private Instant departureTime;

    @Column(nullable = false)
    private int totalSeats;

    @Column(nullable = false)
    private int availableSeats;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public enum Status {
        SCHEDULED, STARTED, COMPLETED, CANCELLED
    }
}