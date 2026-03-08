# API Guide

## Base URL

- Local: `http://localhost:8080`

## Auth

- Login response returns `accessToken`
- Protected APIs require `Authorization: Bearer {accessToken}`
- `POST /api/queue/{eventId}/issue` requires `ROLE_ADMIN`
- `GET /api/queue/{eventId}/token` is enabled only in the `test` profile

근거:
- `src/main/java/com/example/ticketing/api/auth/AuthController.java`
- `src/main/java/com/example/ticketing/config/SecurityConfig.java`
- `src/main/resources/application-test.yml`

## Example cURL

### 1. Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "loaduser001@example.com",
    "password": "password123"
  }'
```

### 2. Enter queue and check status

```bash
curl -X POST http://localhost:8080/api/queue/1/enter \
  -H "Authorization: Bearer ${TOKEN}"

curl http://localhost:8080/api/queue/1/me \
  -H "Authorization: Bearer ${TOKEN}"
```

### 3. Get entry token (`test` profile)

```bash
curl http://localhost:8080/api/queue/1/token \
  -H "Authorization: Bearer ${TOKEN}"
```

기본 프로파일에는 현재 사용자의 `entryToken` 문자열을 조회하는 공개 API가 없습니다. 부하 테스트와 API 재현은 `test` 프로파일의 토큰 조회 API를 기준으로 작성했습니다.

### 4. Request payment

```bash
curl -X POST http://localhost:8080/api/payments/request \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "eventId": 1,
    "entryToken": "'"${ENTRY_TOKEN}"'",
    "seatOption": "STANDARD",
    "amount": 110000
  }'
```
