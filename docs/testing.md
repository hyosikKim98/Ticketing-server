# Testing & Verification

코드 근거 기반으로 현재 레포에서 수행 가능한 테스트/검증 방법을 정리합니다.

근거 파일:
- `build.gradle`
- `src/test/java/com/example/ticketing/queue/QueueServiceTest.java`
- `src/test/java/com/example/ticketing/payment/PaymentRequestKafkaConsumerIdempotencyTest.java`

## 실행 명령

전체 테스트:

```bash
./gradlew test
```

특정 테스트만 실행:

```bash
./gradlew test --tests com.example.ticketing.queue.QueueServiceTest
./gradlew test --tests com.example.ticketing.payment.PaymentRequestKafkaConsumerIdempotencyTest
```

## 현재 테스트 커버리지(핵심 시나리오)

1. Queue 중복 진입 안정성
- 대상: `QueueServiceTest#duplicateEnterKeepsStablePosition`
- 검증: 동일 사용자의 중복 `enter` 호출 시 순번이 유지되는지 확인

2. Kafka 소비 idempotency
- 대상: `PaymentRequestKafkaConsumerIdempotencyTest#sameEventConsumedTwiceCreatesSingleRow`
- 검증:
- 동일 이벤트 2회 소비 시 `payment_requests` 1건만 저장
- 재고(`availableQuantity`)는 1회만 감소

## 수동 검증 체크리스트

1. 인프라 기동

```bash
docker compose up -d
```

2. API 기본 흐름
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/queue/{eventId}/enter`
- `POST /api/queue/{eventId}/issue` (ADMIN)
- `POST /api/payments/request`

3. Kafka/DLT 확인
- 결제 요청 발행 후 `payment-requests` 소비 로그 확인
- 실패 유도 시 `payment-requests.DLT` 적재 여부 확인

## 검증 시 주의사항

- `PaymentRequestKafkaConsumerIdempotencyTest`는 Testcontainers(PostgreSQL)를 사용합니다.
- 로컬에서 Docker가 실행 중이어야 합니다.
