package com.example.ticketing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.TicketingApplication;
import com.example.ticketing.domain.entity.Event;
import com.example.ticketing.domain.entity.TicketInventory;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.domain.repository.TicketInventoryRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import com.example.ticketing.infra.kafka.PaymentRequestKafkaConsumer;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TicketingApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.listener.auto-startup=false",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
@Transactional
class PaymentRequestKafkaConsumerIdempotencyTest {

    @Autowired
    private PaymentRequestKafkaConsumer consumer;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketInventoryRepository ticketInventoryRepository;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Test
    void sameEventConsumedTwiceCreatesSingleRow() {
        Event event = eventRepository.save(new Event("Concert", "Seoul", Instant.now().plusSeconds(3600)));
        ticketInventoryRepository.save(new TicketInventory(event, 100, 100));

        PaymentRequestCreatedEvent duplicate = new PaymentRequestCreatedEvent(
            "idem-1",
            1L,
            event.getId(),
            "VIP",
            150000L
        );

        consumer.listen(duplicate, null);
        consumer.listen(duplicate, null);

        assertThat(paymentRequestRepository.count()).isEqualTo(1);
        TicketInventory inventory = ticketInventoryRepository.findByEventIdForUpdate(event.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(99);
    }
}
