# Troubleshooting

## 1. 결제 요청은 발행되는데 Kafka consumer가 동작하지 않음

- 문제
  - `publish()` 로그는 남지만 `listen()` 로그가 보이지 않고 `payment_requests`가 쌓이지 않습니다.
- 원인
  - Kafka producer와 consumer factory는 있어도 `@KafkaListener`가 실제로 호출되려면 리스너 인프라가 정상 기동돼야 합니다.
  - 확인 기준 파일:
    - `src/main/java/com/example/ticketing/config/KafkaConfig.java`
    - `src/main/java/com/example/ticketing/infra/kafka/PaymentRequestKafkaConsumer.java`
- 해결
  - 앱 시작 로그에서 listener container 시작 여부를 먼저 확인합니다.
  - `payment-requests` 토픽 구독, 직렬화, bootstrap server 연결 상태를 함께 점검합니다.
  - TODO(근거: `src/main/java/com/example/ticketing/config/KafkaConfig.java`): 실제 운영 환경의 listener start 로그 예시를 문서화하면 재현 체크가 더 쉬워집니다.
- 결과
  - 관측 포인트:
    - `payment.consume.received` 로그 발생 여부
    - `ticketing_payment_publish_total` 증가 후 `payment_requests` row 생성 여부
    - `ticket_inventory.available_quantity` 감소 여부

## 2. 토큰은 만료됐는데 다음 대기자가 입장하지 않음

- 문제
  - 사용자의 입장 토큰 TTL은 끝났는데 활성 슬롯이 반환되지 않아 대기열이 멈춘 것처럼 보일 수 있습니다.
- 원인
  - 토큰 키 TTL만으로는 `active_slots:{eventId}` 정리가 보장되지 않기 때문입니다.
  - 확인 기준 파일:
    - `src/main/java/com/example/ticketing/application/queue/QueueService.java`
    - `src/main/java/com/example/ticketing/application/queue/QueueSlotScheduler.java`
- 해결
  - `QueueSlotScheduler`가 30초마다 만료된 활성 슬롯을 정리하고 `fillAvailableSlots()`를 다시 호출합니다.
  - `QueueService.cleanupExpiredSlots()`는 만료 score를 기준으로 Redis ZSET에서 사용자를 제거합니다.
- 결과
  - 정량 수치가 없다면 다음을 측정합니다.
    - `ticketing_queue_slot_expire_total` 증가량
    - `ticketing_queue_active_slots`가 20 이하로 유지되는지
    - `ticketing_queue_waiting_users`가 다시 감소하기 시작하는지
