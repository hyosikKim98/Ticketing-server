package com.example.ticketing.application.inventory;

import com.example.ticketing.domain.entity.TicketInventory;
import com.example.ticketing.domain.repository.TicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TicketInventoryService {

    private final TicketInventoryRepository ticketInventoryRepository;

    @Transactional
    public void reserveOne(Long eventId) {
        TicketInventory inventory = ticketInventoryRepository.findByEventIdForUpdate(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));
        try {
            inventory.decrease(1);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sold out");
        }
    }
}
