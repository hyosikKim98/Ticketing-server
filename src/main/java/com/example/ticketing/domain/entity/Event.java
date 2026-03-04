package com.example.ticketing.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String venue;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Event(String name, String venue, Instant startsAt) {
        this.name = name;
        this.venue = venue;
        this.startsAt = startsAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

}
