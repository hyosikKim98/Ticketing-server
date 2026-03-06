# Troubleshooting

## 1) 결제 요청이 중복 처리되는 문제

### 문제(증상)

- 클라이언트 재시도나 네트워크 지연으로 동일 결제 요청이 여러 번 들어오면 중복 결제 요청 생성 위험이 있습니다.

### 원인(근거 파일)

- 비동기 처리(Kafka) 특성상 같은 의미의 이벤트가 재전송될 수 있습니다.
- 근거:
- `src/main/java/com/example/ticketing/application/payment/PaymentApplicationService.java`
- `src/main/java/com/example/ticketing/application/payment/PaymentRequestService.java`
- `src/main/java/com/example/ticketing/domain/repository/PaymentRequestRepository.java`
- `src/main/resources/db/migration/V1__init_schema.sql`

### 해결(적용 내용)

- API 레벨: `existsByUserIdAndEventIdAndSeatOption` 중복 체크.
- Redis 레벨: `payment_guard:{eventId}:{userId}:{seatOption}` 키로 짧은 시간 중복 요청 차단.
- DB 레벨: `idempotency_key` 유니크 제약 + `ON CONFLICT DO NOTHING` 삽입으로 최종 중복 차단.

### 결과

- 동일 idempotency 요청이 반복되어도 `payment_requests` 레코드는 1건만 생성됩니다.
- 관측 포인트:
- `payment.consume.skip-duplicate` 로그 발생 수
- `payment_requests` 테이블의 `idempotency_key` 충돌 횟수

## 2) Kafka 소비 실패 시 처리 정체/유실 우려

### 문제(증상)

- 결제 이벤트 처리 중 예외가 발생하면 동일 메시지 재처리 또는 처리 누락에 대한 우려가 생길 수 있습니다.

### 원인(근거 파일)

- Kafka Consumer는 수동 ack 모드이며, 실패 시 재시도 정책에 따라 동작합니다.
- 근거:
- `src/main/java/com/example/ticketing/config/KafkaConfig.java`
- `src/main/java/com/example/ticketing/infra/kafka/PaymentRequestKafkaConsumer.java`

### 해결(적용 내용)

- `MANUAL_IMMEDIATE` ack 모드로 성공 시점에만 커밋.
- `DefaultErrorHandler(FixedBackOff(1000, 3))`로 1초 간격 3회 재시도.
- 재시도 실패 메시지는 `payment-requests.DLT`로 격리 전송.
- `setCommitRecovered(true)`로 DLT 전송 후 오프셋 커밋해 전체 소비 정체를 방지.

### 결과

- 단건 실패가 전체 스트림을 장시간 막는 상황을 줄일 수 있습니다.
- 관측 포인트:
- DLT 토픽 적재 건수 증가 추이
- consume 실패 대비 DLT 전환 비율
- TODO: 현재 DLT 재처리 Consumer는 코드에 없음 (`src/main/java/com/example/ticketing/infra/kafka` 기준). 운영 정책에 따라 후속 처리 컴포넌트 추가 필요.
