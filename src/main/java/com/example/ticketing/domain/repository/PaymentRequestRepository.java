package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.entity.PaymentRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByUserIdAndEventIdAndSeatOption(Long userId, Long eventId, String seatOption);

    Optional<PaymentRequest> findByIdempotencyKey(String idempotencyKey);
}
