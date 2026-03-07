# Testing & Verification

코드 근거 기반으로 현재 레포에서 수행 가능한 테스트/검증 방법을 정리합니다.

근거 파일:
- `build.gradle`
- `src/test/java/com/example/ticketing/queue/QueueServiceTest.java`
- `src/test/java/com/example/ticketing/payment/PaymentRequestKafkaConsumerIdempotencyTest.java`
- `src/main/java/com/example/ticketing/api/auth/AuthController.java`
- `src/main/java/com/example/ticketing/api/event/EventController.java`
- `src/main/java/com/example/ticketing/api/queue/QueueController.java`
- `src/main/java/com/example/ticketing/api/payment/PaymentController.java`
- `src/main/java/com/example/ticketing/config/SecurityConfig.java`
- `src/main/resources/db/migration/V1__init_schema.sql`

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
- `POST /api/auth/login`
- `POST /api/queue/{eventId}/enter`
- `POST /api/queue/{eventId}/issue` (ADMIN)
- `POST /api/payments/request`

3. Kafka/DLT 확인
- 결제 요청 발행 후 `payment-requests` 소비 로그 확인
- 실패 유도 시 `payment-requests.DLT` 적재 여부 확인

## JMeter 시나리오

추가된 시나리오 파일:
- `perf/jmeter/ticketing-flow.jmx`
- `perf/jmeter/users.csv`

시나리오 범위:
- 시드 계정 로그인
- 이벤트 목록 조회
- 대기열 진입
- 내 대기열 순번 조회

근거 API:
- `POST /api/auth/login`
- `GET /api/events`
- `POST /api/queue/{eventId}/enter`
- `GET /api/queue/{eventId}/me`

### 실행 전 준비

1. 애플리케이션과 인프라를 기동합니다.

```bash
docker compose up -d
./gradlew bootRun --args='--app.seed.enabled=true'
```

2. 시드 데이터가 자동으로 준비됩니다.

시드 코드가 아래 데이터를 넣습니다.
- `admin@example.com` / `password123` / `ADMIN`
- `loaduser01@example.com` ~ `loaduser20@example.com` / `password123`
- 이벤트 1건: `Load Test Concert`
- 재고 1000건

근거:
- `src/main/java/com/example/ticketing/application/auth/AuthService.java`
- `src/main/java/com/example/ticketing/config/SecurityConfig.java`
- `src/main/java/com/example/ticketing/config/SeedDataInitializer.java`
- `src/main/resources/db/migration/V1__init_schema.sql`

### 실행 방법

GUI:

```bash
jmeter -t perf/jmeter/ticketing-flow.jmx
```

비GUI:

```bash
jmeter -n \
  -t perf/jmeter/ticketing-flow.jmx \
  -l perf/jmeter/result.jtl \
  -Jusers=20 \
  -JrampUp=20
```

### 해석 포인트

- `User Flow` 스레드 그룹은 현재 코드만으로 바로 실행 가능한 기본 부하 시나리오입니다.
- 시나리오는 시드된 `loaduser01@example.com` ~ `loaduser20@example.com` 계정으로 로그인한 뒤 조회/대기열 API를 호출합니다.
- 현재 JMX는 사용자 기본 흐름만 포함합니다. 관리자 토큰 발급과 결제 요청은 별도 수동 검증 대상으로 남겨둡니다.

## 검증 시 주의사항

- `PaymentRequestKafkaConsumerIdempotencyTest`는 Testcontainers(PostgreSQL)를 사용합니다.
- 로컬에서 Docker가 실행 중이어야 합니다.
- 시드 데이터는 `app.seed.enabled=true`일 때만 들어갑니다. 기본값은 `false`입니다.
