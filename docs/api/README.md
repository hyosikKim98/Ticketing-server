# API Guide

## Base URL

- `http://localhost:8080`
- 근거: `src/main/resources/application.yml`에 `server.port` 오버라이드가 없음

## Auth

- 방식: `Authorization: Bearer <accessToken>`
- 토큰 발급: `POST /api/auth/login`
- 권한 규칙:
- 누구나 호출 가능: `/api/auth/**`, `GET /api/events/**`
- ADMIN 필요: `POST /api/queue/{eventId}/issue`
- 그 외 인증 필요

근거:
- `src/main/java/com/example/ticketing/config/SecurityConfig.java`
- `src/main/java/com/example/ticketing/security/JwtAuthenticationFilter.java`

## 대표 curl 예시 3개

### 1) 로그인

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### 2) 이벤트 목록 조회(인증 불필요)

```bash
curl http://localhost:8080/api/events
```

### 3) 결제 요청 발행(인증 필요)

```bash
curl -X POST http://localhost:8080/api/payments/request \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "eventId": 1,
    "entryToken": "1:1700000000:123456:1",
    "seatOption": "VIP",
    "amount": 150000,
    "idempotencyKey": "pay-req-001"
  }'
```

자세한 스키마/응답 코드는 [openapi.yaml](openapi.yaml) 참고.
