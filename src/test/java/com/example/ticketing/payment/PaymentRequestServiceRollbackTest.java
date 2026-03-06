package com.example.ticketing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ticketing.TicketingApplication;
import com.example.ticketing.application.payment.PaymentRequestService;
import com.example.ticketing.domain.entity.Event;
import com.example.ticketing.domain.entity.TicketInventory;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.domain.repository.TicketInventoryRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = TicketingApplication.class)
@Testcontainers
@TestPropertySource(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
class PaymentRequestServiceRollbackTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("ticketing_rollback_test")
        .withUsername("ticket")
        .withPassword("ticket");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketInventoryRepository ticketInventoryRepository;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Test
    void rollbackInsertedPaymentRequestWhenSoldOut() {
        Event event = eventRepository.save(new Event("Soldout", "Seoul", Instant.now().plusSeconds(3600)));
        ticketInventoryRepository.save(new TicketInventory(event, 0, 0));

        String key = "idem-rollback-1";
        PaymentRequestCreatedEvent request = new PaymentRequestCreatedEvent(
            key,
            99L,
            event.getId(),
            "VIP",
            120000L
        );

        assertThatThrownBy(() -> paymentRequestService.recordFromEvent(request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        assertThat(paymentRequestRepository.findByIdempotencyKey(key)).isEmpty();
    }
}
