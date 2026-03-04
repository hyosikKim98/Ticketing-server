package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.entity.TicketInventory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TicketInventoryRepository extends JpaRepository<TicketInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketInventory t where t.event.id = :eventId")
    Optional<TicketInventory> findByEventIdForUpdate(Long eventId);
}
