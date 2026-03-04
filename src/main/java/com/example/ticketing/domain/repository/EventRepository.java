package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
