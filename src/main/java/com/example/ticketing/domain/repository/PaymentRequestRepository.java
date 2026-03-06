package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.entity.PaymentRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByUserIdAndEventIdAndSeatOption(Long userId, Long eventId, String seatOption);

    Optional<PaymentRequest> findByIdempotencyKey(String idempotencyKey);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO payment_requests
            (user_id, event_id, seat_option, amount, status, idempotency_key, created_at)
        VALUES
            (:userId, :eventId, :seatOption, :amount, :status, :idempotencyKey, NOW())
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true)
//    @Modifying
//    @Query(value = """
//        INSERT INTO payment_requests (user_id, event_id, seat_option, amount, status, idempotency_key, created_at)
//        SELECT :userId, :eventId, :seatOption, :amount, :status, :idempotencyKey, NOW()
//        WHERE NOT EXISTS (
//            SELECT 1
//            FROM payment_requests
//            WHERE idempotency_key = :idempotencyKey
//        )
//        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("eventId") Long eventId,
            @Param("seatOption") String seatOption,
            @Param("amount") Long amount,
            @Param("status") String status,
            @Param("idempotencyKey") String idempotencyKey
    );
}
