package com.example.ticketing.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ticket_inventory")
@Getter
@NoArgsConstructor
public class TicketInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer availableQuantity;

    @Version
    private Long version;

    public TicketInventory(Event event, Integer totalQuantity, Integer availableQuantity) {
        this.event = event;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
    }

    public void decrease(int quantity) {
        if (availableQuantity < quantity) {
            throw new IllegalStateException("Not enough stock");
        }
        this.availableQuantity -= quantity;
    }
}
