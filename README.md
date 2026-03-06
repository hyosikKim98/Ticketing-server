# Ticketing Server

실시간 대기열과 비동기 결제 요청 처리를 결합해 티켓 예매 트래픽 피크를 안정적으로 처리하는 Spring Boot 백엔드입니다.

## 핵심 가치

- 대기열 기반 입장 제어로 순간 트래픽 급증 시 API 과부하를 완화합니다.
- 결제 요청을 Kafka 비동기 처리로 분리해 응답 지연을 줄이고 처리 내구성을 높입니다.
- JWT 인증/권한 제어로 사용자/관리자 동작 경계를 명확히 유지합니다.

## 핵심 기능

- 회원가입/로그인(JWT 발급)
- 이벤트 목록/단건 조회
- Redis ZSET 기반 대기열 진입/순번 조회
- 관리자 대기열 상위 N명 입장 토큰 발급
- Kafka 기반 결제 요청 비동기 발행/소비 + 중복 방지(idempotency)

## 기술 스택

- 서버: Java 21, Spring Boot, Spring Web, Spring Security, Spring Validation
- 데이터: PostgreSQL, Spring Data JPA, Flyway
- 캐시/큐: Redis, Apache Kafka
- 인증: JWT(`jjwt`)
- 빌드/테스트: Gradle, JUnit 5, Testcontainers, Mockito
- 인프라(로컬): Docker Compose(PostgreSQL/Redis/Kafka)

## 빠른 시작

### 1) 사전 준비

- Java 21
- Docker / Docker Compose

### 2) 의존 인프라 실행

```bash
docker compose up -d
```

실행 대상:
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`

근거: [docker-compose.yml](/Users/hyosik981010/Desktop/study/ticket/docker-compose.yml)

### 3) 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 설정 근거:
- DB/Redis/Kafka 연결: [application.yml](/Users/hyosik981010/Desktop/study/ticket/src/main/resources/application.yml)
- 빌드/의존성: [build.gradle](/Users/hyosik981010/Desktop/study/ticket/build.gradle)

### 4) 테스트 실행

```bash
./gradlew test
```

## 인증/인가 정책

- `permitAll`
- `/api/auth/**`
- `GET /api/events/**`
- `ROLE_ADMIN` 필요
- `POST /api/queue/*/issue`
- 그 외 요청은 인증 필요

근거: [SecurityConfig.java](/Users/hyosik981010/Desktop/study/ticket/src/main/java/com/example/ticketing/config/SecurityConfig.java)

## 주요 API 흐름

1. 사용자 대기열 진입: `POST /api/queue/{eventId}/enter`
2. 관리자 토큰 발급: `POST /api/queue/{eventId}/issue`
3. 사용자 결제 요청 발행: `POST /api/payments/request` (`202 Accepted`)
4. Kafka Consumer가 결제요청 저장/재고 차감 처리

근거:
- [QueueController.java](/Users/hyosik981010/Desktop/study/ticket/src/main/java/com/example/ticketing/api/queue/QueueController.java)
- [PaymentController.java](/Users/hyosik981010/Desktop/study/ticket/src/main/java/com/example/ticketing/api/payment/PaymentController.java)
- [PaymentRequestProducer.java](/Users/hyosik981010/Desktop/study/ticket/src/main/java/com/example/ticketing/infra/kafka/PaymentRequestProducer.java)
- [PaymentRequestKafkaConsumer.java](/Users/hyosik981010/Desktop/study/ticket/src/main/java/com/example/ticketing/infra/kafka/PaymentRequestKafkaConsumer.java)

## 프로젝트 구조

```text
src/main/java/com/example/ticketing
├── api            # REST Controller + DTO
├── application    # 유스케이스/서비스
├── domain         # 엔티티/리포지토리
├── infra          # Kafka Producer/Consumer
├── security       # JWT 필터/토큰/Principal
└── config         # Security/Kafka 설정

src/main/resources
└── db/migration   # Flyway 마이그레이션
```

## 문서 링크

- 문서 포털: [docs/INDEX.md](/Users/hyosik981010/Desktop/study/ticket/docs/INDEX.md)
- OpenAPI: [docs/api/openapi.yaml](/Users/hyosik981010/Desktop/study/ticket/docs/api/openapi.yaml)
- OpenAPI 사용법: [docs/api/README.md](/Users/hyosik981010/Desktop/study/ticket/docs/api/README.md)
- ERD: [docs/erd.md](/Users/hyosik981010/Desktop/study/ticket/docs/erd.md)
- Architecture: [docs/architecture.md](/Users/hyosik981010/Desktop/study/ticket/docs/architecture.md)
- Troubleshooting: [docs/troubleshooting.md](/Users/hyosik981010/Desktop/study/ticket/docs/troubleshooting.md)
