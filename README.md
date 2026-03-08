# Ticketing Server

Redis 대기열, Kafka 비동기 결제, 20-slot 입장 제어를 결합해 이벤트 티켓 트래픽 피크를 안정적으로 처리하는 Spring Boot 서버입니다.

## 핵심 가치

- 대기열과 활성 슬롯 제한으로 동시 결제 인원을 제어합니다.
- Kafka 비동기 처리로 결제 요청과 재고 차감을 분리합니다.
- JWT 인증과 관리자 발급 기능으로 사용자/운영자 권한 경계를 유지합니다.
- Prometheus, Grafana, JMeter 기반으로 부하 테스트와 관측을 함께 검증할 수 있습니다.

## 핵심 기능

- 회원가입, 로그인, JWT 인증
- 이벤트 조회와 Redis ZSET 기반 대기열 진입
- 최대 20명 활성 슬롯 기반 입장 토큰 발급
- Kafka 기반 결제 요청 발행 및 소비
- Prometheus, Grafana, JMeter 기반 관측과 부하 테스트

## 기술 스택

- 서버: Java 21, Spring Boot, Spring Web, Spring Security, Spring Validation
- 데이터: PostgreSQL, Spring Data JPA, Flyway
- 캐시/대기열: Redis
- 메시징: Apache Kafka
- 인증: JWT (`jjwt`)
- 관측: Spring Boot Actuator, Micrometer, Prometheus, Grafana
- 테스트/부하: JUnit 5, Mockito, Testcontainers, JMeter
- 인프라(로컬): Docker Compose

## 빠른 시작

### 1. 사전 준비

- Java 21
- Docker / Docker Compose
- JMeter CLI
- 기본 포트
  - App: `8080`
  - PostgreSQL: `5432`
  - Redis: `6379`
  - Kafka: `9092`
  - Prometheus: `9090`
  - Grafana: `3000`

### 2. 로컬 인프라 실행

```bash
docker compose down -v
docker compose up -d
```

근거:
- [docker-compose.yml](docker-compose.yml)
- [monitoring/prometheus.yml](monitoring/prometheus.yml)

### 3. 애플리케이션 실행

기본 실행:

```bash
./gradlew bootRun --args='--app.seed.enabled=true'
```

JMeter mixed-flow 테스트 실행:

```bash
./gradlew bootRun --args='--spring.profiles.active=test --app.seed.enabled=true'
```

`test` 프로파일 차이:
- 입장 토큰 TTL: 2분
- 결제 중복 방지 TTL: 2분
- 테스트용 토큰 조회 API 활성화: `GET /api/queue/{eventId}/token`

근거:
- [src/main/resources/application.yml](src/main/resources/application.yml)
- [src/main/resources/application-test.yml](src/main/resources/application-test.yml)
- [src/main/java/com/example/ticketing/api/queue/QueueController.java](src/main/java/com/example/ticketing/api/queue/QueueController.java)

### 4. 관측 확인

- Prometheus: [http://localhost:9090](http://localhost:9090)
- Grafana: [http://localhost:3000](http://localhost:3000)
  - 기본 계정: `admin / admin`

노출 메트릭 예시:
- `ticketing_queue_active_slots`
- `ticketing_queue_waiting_users`
- `ticketing_queue_enter_total`
- `ticketing_queue_issue_auto_total`
- `ticketing_queue_slot_release_total`
- `ticketing_queue_slot_expire_total`
- `ticketing_payment_publish_total`

근거:
- [src/main/java/com/example/ticketing/application/queue/QueueMetrics.java](src/main/java/com/example/ticketing/application/queue/QueueMetrics.java)
- [monitoring/grafana/dashboards/ticketing-overview.json](monitoring/grafana/dashboards/ticketing-overview.json)

### 5. JMeter 부하 테스트

200명 mixed-flow 시나리오:

```bash
jmeter -n \
  -t perf/jmeter/queue-slot-mixed-flow.jmx \
  -l perf/jmeter/queue-slot-mixed-flow.jtl \
  -e \
  -o perf/jmeter/report-queue-slot-mixed
```

시나리오 요약:
- 200 users 로그인
- 같은 이벤트에 대기열 진입
- 입장 토큰 획득까지 polling
- 70%는 결제 요청
- 30%는 결제하지 않고 토큰 만료
- 활성 슬롯 20개 유지 여부와 자동 보충 관찰

근거:
- [perf/jmeter/queue-slot-mixed-flow.jmx](perf/jmeter/queue-slot-mixed-flow.jmx)
- [perf/jmeter/users.csv](perf/jmeter/users.csv)

## 인증/인가

- `permitAll`
  - `/api/auth/**`
  - `GET /api/events/**`
  - `/actuator/health`
  - `/actuator/info`
  - `/actuator/prometheus`
- `ROLE_ADMIN`
  - `POST /api/queue/{eventId}/issue`
  - 기타 `/actuator/**`
- 그 외 `/api/**`는 인증 필요

근거:
- [src/main/java/com/example/ticketing/config/SecurityConfig.java](src/main/java/com/example/ticketing/config/SecurityConfig.java)

## 주요 흐름

### 사용자 구매 흐름

1. 로그인 후 JWT 획득
2. `POST /api/queue/{eventId}/enter`
3. 서버가 빈 슬롯이 있으면 입장 토큰 자동 발급
4. 사용자는 `POST /api/payments/request`
5. Kafka consumer가 결제 요청 저장 후 재고 차감
6. 결제 성공 시 슬롯 반환
7. 다음 대기자 자동 입장

### 운영자 흐름

1. 관리자 로그인
2. `POST /api/queue/{eventId}/issue`
3. 상위 N명에게 수동으로 입장 토큰 발급

## 프로젝트 구조

```text
src/main/java/com/example/ticketing
├── api            # Controller + DTO
├── application    # queue / payment / inventory / auth 서비스
├── domain         # Entity + Repository
├── infra          # Kafka producer / consumer
├── security       # JWT filter / principal / token provider
└── config         # Security / Kafka / Seed / Properties

src/main/resources
├── application.yml
├── application-test.yml
└── db/migration

monitoring
├── prometheus.yml
└── grafana

perf/jmeter
├── ticketing-flow.jmx
├── queue-slot-mixed-flow.jmx
├── users.csv
└── README.md
```

## 문서 링크

- 문서 포털: [docs/INDEX.md](docs/INDEX.md)
- OpenAPI: [docs/api/openapi.yaml](docs/api/openapi.yaml)
- API 가이드: [docs/api/README.md](docs/api/README.md)
- ERD: [docs/erd.md](docs/erd.md)
- Architecture: [docs/architecture.md](docs/architecture.md)
- Troubleshooting: [docs/troubleshooting.md](docs/troubleshooting.md)

## 참고

- 테스트용 토큰 조회 API는 `test` 프로파일에서만 활성화됩니다.
- Redis는 볼륨이 없어 `docker compose down` 시 대기열 데이터가 초기화됩니다.
- PostgreSQL, Kafka, Grafana는 named volume을 사용하므로 `docker compose down -v`를 실행할 때 함께 초기화됩니다.
