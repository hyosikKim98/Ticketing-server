package com.example.ticketing.config;

import com.example.ticketing.domain.entity.Event;
import com.example.ticketing.domain.entity.TicketInventory;
import com.example.ticketing.domain.entity.User;
import com.example.ticketing.domain.entity.UserRole;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.TicketInventoryRepository;
import com.example.ticketing.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketInventoryRepository ticketInventoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedEventAndInventory();
    }

    private void seedUsers() {
        createUserIfAbsent("admin@example.com", "password123", UserRole.ADMIN);

        for (int i = 1; i <= 20; i++) {
            createUserIfAbsent(
                "loaduser%02d@example.com".formatted(i),
                "password123",
                UserRole.USER
            );
        }
    }

    private void createUserIfAbsent(String email, String rawPassword, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        userRepository.save(new User(
            email,
            passwordEncoder.encode(rawPassword),
            role
        ));
    }

    private void seedEventAndInventory() {
        Event event = eventRepository.findById(1L).orElseGet(() ->
            eventRepository.save(new Event(
                "Load Test Concert",
                "Seoul Arena",
                Instant.now().plus(7, ChronoUnit.DAYS)
            ))
        );

        boolean inventoryExists = ticketInventoryRepository.findAll().stream()
            .anyMatch(inventory -> inventory.getEvent().getId().equals(event.getId()));

        if (!inventoryExists) {
            ticketInventoryRepository.save(new TicketInventory(event, 1000, 1000));
        }
    }
}
