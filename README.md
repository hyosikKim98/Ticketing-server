# Ticketing Server

티켓 오픈처럼 짧은 시간에 많은 사용자가 몰리는 상황을 가정해 만든 예매 서버입니다.

핵심 목표는 단순히 "예매 API를 만든 것"이 아니라, 많은 사용자가 동시에 들어와도 결제 단계와 재고 차감이 무너지지 않도록 흐름을 제어하는 것입니다.

<p align="center">
  <img src="./img/ticketing-dashboard.png" width="900" alt="Ticketing Grafana dashboard">
</p>

## 1. 프로젝트 개요

이 프로젝트는 티켓 오픈 순간에 발생하는 동시 접속, 중복 결제 요청, 재고 초과 차감 문제를 해결하기 위해 만든 예매 시스템입니다.

기존 방식처럼 모든 사용자를 바로 결제 API로 보내면 다음 문제가 생길 수 있습니다.

- 많은 요청이 한 번에 DB 재고 row로 몰림
- 결제 처리 지연이 API 응답 지연으로 이어짐
- 같은 요청이 재시도되면 중복 결제 요청이 저장될 수 있음
- 입장 토큰이 만료됐는데 슬롯이 반환되지 않으면 대기열이 멈출 수 있음

그래서 이 프로젝트는 사용자를 바로 결제시키지 않고, 다음 순서로 처리합니다.

```text
사용자
-> 대기열 입장
-> 결제 가능한 20명만 입장 토큰 발급
-> 결제 요청은 Kafka에 발행
-> Consumer가 DB에 결제 요청 저장
-> DB에서 재고 차감
-> 성공 후 다음 대기자 입장
```

## 2. 문제 정의

### 문제 1. 모두가 동시에 결제하면 DB 재고가 병목이 됨

티켓 수량은 한정되어 있습니다. 그런데 티켓 오픈 직후 모든 사용자가 동시에 결제 요청을 보내면 같은 재고 데이터에 요청이 몰립니다.

이때 재고 차감을 조심해서 처리하지 않으면 실제 수량보다 더 많이 판매될 수 있습니다. 반대로 DB lock만 강하게 걸면 요청이 줄을 서면서 API 응답이 느려질 수 있습니다.

### 문제 2. 결제 처리를 HTTP 요청 안에서 모두 끝내면 느려짐

결제 요청 저장, 중복 확인, 재고 차감까지 한 번의 API 요청에서 모두 처리하면 한 단계가 느려질 때 전체 응답이 같이 느려집니다.

또 Kafka 같은 메시지 큐가 없으면 처리 실패 후 재시도하거나, 실패한 요청을 따로 추적하기도 어렵습니다.

### 문제 3. 입장 토큰 만료와 슬롯 반환은 별도 문제임

입장 토큰은 시간이 지나면 만료됩니다. 하지만 토큰 key가 사라졌다고 해서 활성 슬롯 목록에서도 자동으로 빠지는 것은 아닙니다.

이 정리가 안 되면 실제로는 결제 가능한 자리가 비어 있어도 시스템은 아직 자리가 찬 것으로 판단할 수 있습니다.

### 왜 중요한가

- 초과 판매를 막아야 합니다.
- 결제 단계에 들어가는 사용자 수를 제한해야 합니다.
- 중복 요청이 와도 결제 요청과 재고가 한 번만 반영되어야 합니다.
- 운영 중에는 대기자 수, 활성 슬롯 수, 결제 요청 수를 볼 수 있어야 합니다.

## 3. 해결 방식

### 전체 구조

```text
Client
  -> Spring Boot API
  -> Redis 대기열 / 활성 슬롯
  -> Kafka payment-requests topic
  -> Kafka Consumer
  -> PostgreSQL 결제 요청 저장 / 재고 차감
  -> Prometheus / Grafana 모니터링
```

### 핵심 설계 1. Redis로 대기열을 관리함

사용자가 예매를 시도하면 바로 결제 단계로 보내지 않고 Redis 대기열에 넣습니다.

- `queue:{eventId}`: 대기 중인 사용자 목록
- `active_slots:{eventId}`: 현재 결제 단계에 들어간 사용자 목록
- `entry_token:{eventId}:{userId}`: 결제 단계에 들어갈 수 있는 입장 토큰

이 프로젝트에서는 결제 단계에 들어갈 수 있는 사용자를 기본 20명으로 제한했습니다.

대기열에서 사용자를 꺼내고, 입장 토큰을 만들고, 활성 슬롯에 넣는 작업은 Redis Lua script로 한 번에 처리합니다. 여러 요청이 동시에 들어와도 20명을 넘겨 발급하지 않기 위해서입니다.

근거:
- [`QueueService`](src/main/java/com/example/ticketing/application/queue/QueueService.java)
- [`application.yml`](src/main/resources/application.yml)

### 핵심 설계 2. 결제 요청은 Kafka로 분리함

결제 API는 결제를 끝까지 처리하지 않습니다. 먼저 입장 토큰이 맞는지 확인하고, 중복 요청인지 확인한 뒤 Kafka에 결제 요청 이벤트를 발행합니다.

그 다음 실제 저장과 재고 차감은 Kafka Consumer가 처리합니다.

이렇게 나눈 이유는 API가 모든 일을 직접 처리하지 않게 하기 위해서입니다. API는 "요청 접수"까지만 빠르게 끝내고, 무거운 처리는 뒤에서 처리합니다.

근거:
- [`PaymentApplicationService`](src/main/java/com/example/ticketing/application/payment/PaymentApplicationService.java)
- [`PaymentRequestKafkaConsumer`](src/main/java/com/example/ticketing/infra/kafka/PaymentRequestKafkaConsumer.java)

### 핵심 설계 3. 중복 결제 요청은 DB에서 한 번 더 막음

Kafka는 같은 메시지를 두 번 전달할 수 있습니다. 그래서 Consumer는 같은 결제 요청이 두 번 들어와도 DB에 한 번만 저장되도록 처리했습니다.

`payment_requests.idempotency_key`에 unique 제약을 걸고, insert 할 때 `ON CONFLICT DO NOTHING`을 사용했습니다.

즉, 같은 요청이 다시 들어오면 다음 일이 일어납니다.

```text
이미 저장된 idempotency_key 확인
-> 새 row 저장 안 함
-> 재고 차감도 안 함
```

근거:
- [`PaymentRequestRepository`](src/main/java/com/example/ticketing/domain/repository/PaymentRequestRepository.java)
- [`PaymentRequestKafkaConsumerIdempotencyTest`](src/test/java/com/example/ticketing/payment/PaymentRequestKafkaConsumerIdempotencyTest.java)

### 핵심 설계 4. DB 저장 성공 후 슬롯을 반환함

결제 요청 저장과 재고 차감은 DB 트랜잭션 안에서 처리합니다.

중요한 점은 Redis 슬롯을 먼저 반환하지 않는다는 것입니다. DB 처리가 실패했는데 슬롯만 반환하면, 실제 결제는 실패했는데 다음 사용자가 들어오는 상태가 될 수 있습니다.

그래서 DB commit이 끝난 뒤에만 Redis 슬롯을 반환합니다.

근거:
- [`PaymentRequestService`](src/main/java/com/example/ticketing/application/payment/PaymentRequestService.java)
- [`PaymentRequestServiceRollbackTest`](src/test/java/com/example/ticketing/payment/PaymentRequestServiceRollbackTest.java)

## 4. 설계 판단

### 판단 1. 왜 Redis 대기열을 사용했는가

#### 고려한 방법

- 모든 사용자를 바로 결제 API로 보냄
- DB에서 대기열까지 관리
- Redis로 대기열과 활성 슬롯 관리

#### 선택

Redis로 대기열과 활성 슬롯을 관리했습니다.

#### 이유

- 대기열 순위 조회가 빠릅니다.
- TTL을 이용해 입장 토큰 만료를 다루기 쉽습니다.
- Lua script를 사용하면 여러 Redis 명령을 원자적으로 처리할 수 있습니다.

#### 트레이드오프

Redis 상태와 DB 상태는 같은 트랜잭션으로 묶이지 않습니다. 그래서 DB commit 이후에 슬롯을 반환하는 식으로 불일치 가능성을 줄였습니다.

### 판단 2. 왜 재고 차감은 DB lock으로 처리했는가

#### 고려한 방법

- Redis lock
- DB optimistic lock
- DB pessimistic lock
- 조건부 update

#### 선택

현재 구현은 PostgreSQL pessimistic write lock을 사용합니다.

#### 이유

재고는 최종적으로 DB에 저장되는 핵심 데이터입니다. 그래서 재고를 읽고 차감하는 작업을 DB 트랜잭션 안에서 묶었습니다.

#### 트레이드오프

DB lock은 요청이 많이 몰리면 대기 시간이 생길 수 있습니다. 이 문제를 줄이기 위해 앞단의 Redis 활성 슬롯을 20명으로 제한했습니다.

추후에는 `UPDATE ticket_inventory SET available_quantity = available_quantity - 1 WHERE available_quantity > 0` 방식과 비교해볼 수 있습니다.

### 판단 3. 왜 Kafka를 사용했는가

#### 고려한 방법

- API 요청 안에서 결제 요청 저장과 재고 차감을 모두 처리
- Kafka에 이벤트를 발행하고 Consumer가 처리

#### 선택

Kafka를 사용해 결제 요청 접수와 실제 처리를 분리했습니다.

#### 이유

- API 응답 경로를 짧게 만들 수 있습니다.
- Consumer 실패 시 재시도와 DLT 처리를 분리해서 볼 수 있습니다.
- 중복 소비는 DB unique key로 방어할 수 있습니다.

#### 트레이드오프

API가 `PUBLISHED`를 반환해도 DB 저장은 아직 끝나지 않았을 수 있습니다. 즉, 최종 상태 조회 API나 후속 상태 관리가 필요합니다.

## 5. 결과

### JMeter 부하 테스트 결과

저장소에 남아 있는 [`statistics.json`](perf/jmeter/report-queue-slot-mixed/statistics.json) 기준입니다.

| 항목 | 결과 |
| --- | --- |
| 시나리오 | 200명 mixed-flow |
| 흐름 | 로그인 -> 대기열 진입 -> 토큰 확인 -> 결제 또는 만료 대기 |
| 전체 요청 수 | 25,132 |
| 에러율 | 0.00% |
| 평균 응답 시간 | 8.79ms |
| p90 / p95 / p99 | 12ms / 14ms / 18ms |
| 처리량 | 59.73 requests/sec |
| 결제 요청 평균 응답 시간 | 13.24ms |

이 결과로 확인한 것은 다음입니다.

- 200명이 동시에 들어오는 시나리오에서 요청 에러가 발생하지 않았습니다.
- 대기열 진입, 토큰 polling, 결제 요청 흐름이 끝까지 수행되었습니다.
- 비교 테스트 결과는 없으므로 "기존 대비 몇 배 개선" 같은 주장은 하지 않았습니다.

근거:
- [`queue-slot-mixed-flow.jmx`](perf/jmeter/queue-slot-mixed-flow.jmx)
- [`jmeter.log`](jmeter.log)

### 자동화 테스트로 확인한 내용

- 같은 사용자가 대기열에 다시 들어와도 순번이 흔들리지 않습니다.
- 같은 Kafka 이벤트가 두 번 소비되어도 결제 요청은 한 번만 저장됩니다.
- 중복 이벤트가 들어와도 재고는 한 번만 차감됩니다.
- 품절로 재고 차감이 실패하면 결제 요청 저장도 rollback됩니다.

근거:
- [`QueueServiceTest`](src/test/java/com/example/ticketing/queue/QueueServiceTest.java)
- [`PaymentRequestServiceTest`](src/test/java/com/example/ticketing/payment/PaymentRequestServiceTest.java)
- [`PaymentRequestKafkaConsumerIdempotencyTest`](src/test/java/com/example/ticketing/payment/PaymentRequestKafkaConsumerIdempotencyTest.java)
- [`PaymentRequestServiceRollbackTest`](src/test/java/com/example/ticketing/payment/PaymentRequestServiceRollbackTest.java)

## 6. 트러블슈팅

### 문제 1. Kafka 발행은 됐는데 DB에 결제 요청이 안 생김

#### 문제

결제 API는 `PUBLISHED`를 반환하지만 `payment_requests` 테이블에 row가 생기지 않았습니다.

#### 원인

Kafka producer 발행 성공과 consumer 처리 성공은 다른 단계입니다. consumer가 topic을 제대로 구독하지 못하거나, 역직렬화에 실패하거나, listener가 실행되지 않으면 DB 저장까지 가지 못합니다.

#### 해결

consumer 로그와 Kafka listener 설정을 확인했습니다. 실패한 메시지는 `DefaultErrorHandler`를 통해 재시도하고, 계속 실패하면 DLT로 보낼 수 있도록 구성했습니다.

#### 결과

API 응답만 보는 것이 아니라 다음 항목을 함께 확인하도록 기준을 잡았습니다.

- `payment.consume.received` 로그
- `payment_requests` row 생성 여부
- `ticketing_payment_publish_total` metric
- 재고 수량 감소 여부

### 문제 2. 토큰이 만료됐는데 대기열이 진행되지 않음

#### 문제

입장 토큰 TTL은 끝났는데 다음 사용자가 들어오지 않는 상황이 생길 수 있습니다.

#### 원인

토큰 key가 만료되어도 `active_slots:{eventId}`에 남아 있는 사용자 정보는 자동으로 지워지지 않습니다.

#### 해결

30초마다 스케줄러가 만료된 활성 슬롯을 정리하고, 빈 슬롯만큼 다음 대기자에게 토큰을 발급하도록 했습니다.

#### 결과

Grafana에서 다음 metric으로 확인할 수 있습니다.

- `ticketing_queue_active_slots`
- `ticketing_queue_waiting_users`
- `ticketing_queue_slot_expire_total`

## 7. 기술 스택

| 영역 | 기술 | 사용 이유 |
| --- | --- | --- |
| Backend | Java 21, Spring Boot | API, 트랜잭션, 스케줄링, actuator 구성 |
| Security | Spring Security, JWT | 로그인 후 stateless 인증 처리 |
| Database | PostgreSQL, JPA, Flyway | 유저, 이벤트, 재고, 결제 요청 저장 |
| Queue | Redis ZSET, Lua script | 대기열 순서와 활성 슬롯을 빠르게 관리 |
| Messaging | Kafka, Spring Kafka | 결제 요청 접수와 실제 처리를 분리 |
| Monitoring | Prometheus, Grafana, Micrometer | 대기열과 결제 흐름을 metric으로 확인 |
| Test | JUnit 5, Mockito, Testcontainers, JMeter | 단위 테스트, 통합 테스트, 부하 테스트 |
| Infra | Docker Compose | 로컬에서 DB, Redis, Kafka, 모니터링 환경 실행 |

## 8. 실행 방법

### 사전 준비

- Java 21
- Docker 또는 Docker Compose
- JMeter 5.6.x 이상

### 인프라 실행

```bash
cd /Users/hyosik981010/Desktop/study/ticket
docker compose up -d
```

### 애플리케이션 실행

```bash
./gradlew bootRun --args='--app.seed.enabled=true'
```

JMeter 시나리오를 실행하려면 테스트용 토큰 조회 API가 필요합니다. 이때는 `test` profile로 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=test --app.seed.enabled=true'
```

seed 데이터 계정:

| 역할 | 이메일 | 비밀번호 |
| --- | --- | --- |
| 관리자 | `admin@example.com` | `password123` |
| 사용자 | `loaduser001@example.com`부터 `loaduser200@example.com` | `password123` |

### 확인 URL

- App: [http://localhost:8080](http://localhost:8080)
- Prometheus: [http://localhost:9090](http://localhost:9090)
- Grafana: [http://localhost:3000](http://localhost:3000), `admin / admin`
- OpenAPI: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)

### 테스트 실행

전체 테스트는 Testcontainers로 PostgreSQL 컨테이너를 실행합니다. Docker daemon이 실행 중이어야 합니다.

```bash
./gradlew test
```

Docker 없이 단위 테스트만 확인하려면 다음처럼 실행할 수 있습니다.

```bash
./gradlew test --tests com.example.ticketing.queue.QueueServiceTest --tests com.example.ticketing.payment.PaymentRequestServiceTest
```

### JMeter 실행

애플리케이션을 `test` profile로 실행한 뒤 아래 명령을 실행합니다.

```bash
jmeter -n -t perf/jmeter/queue-slot-mixed-flow.jmx -l /tmp/queue-slot-mixed-flow.jtl -Jusers=200 -JrampUp=20
```

HTML 리포트 생성:

```bash
jmeter -g /tmp/queue-slot-mixed-flow.jtl -o /tmp/queue-slot-mixed-report
```

## 9. 프로젝트 구조

```text
src/main/java/com/example/ticketing
├── api            # Controller, DTO
├── application    # queue, payment, inventory, auth 서비스
├── domain         # Entity, Repository
├── infra/kafka    # Kafka producer, consumer
├── security       # JWT 인증 필터
└── config         # Security, Kafka, Seed, Properties 설정

src/main/resources
├── application.yml
├── application-test.yml
└── db/migration

perf/jmeter
├── queue-slot-mixed-flow.jmx
├── users.csv
└── report-queue-slot-mixed

monitoring
├── prometheus.yml
└── grafana
```

## 10. 개선 방향

- 재고 차감 방식을 DB lock 방식과 조건부 update 방식으로 나누어 성능을 비교합니다.
- Kafka DLT에 쌓인 메시지를 다시 처리하는 운영용 API를 추가합니다.
- 현재 Redis metric 조회는 `KEYS` 패턴을 사용하므로 운영 환경에서는 `SCAN` 기반으로 바꿉니다.
- 대기열 metric을 전체 기준뿐 아니라 이벤트별로도 볼 수 있게 개선합니다.
- JMeter 결과를 한 번의 로컬 실행이 아니라 반복 가능한 기준값으로 관리합니다.
