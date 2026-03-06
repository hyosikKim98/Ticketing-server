# AGENTS.md

## Goal

- 이 레포는 “최소 노력 대비 효과 큰 문서화 5종 세트(README, OpenAPI, ERD, Architecture, Troubleshooting)”를 만든다.

## Rules

- 문서는 레포의 실제 코드/설정 파일을 근거로 작성(추측 금지).
- 확실하지 않으면 TODO로 남기되 근거 파일 경로를 반드시 적기.
- Mermaid 다이어그램은 한 장에 들어오도록 노드 수를 제한(<=14).
- OpenAPI는 Controller/Router + DTO/Schema 기반으로 작성.
- 문서 생성 후 README에서 docs/INDEX.md 링크가 깨지지 않게 유지.

너는 시니어 소프트웨어 엔지니어 + 테크 라이터다.
목표는 이 레포를 “최소 노력 대비 효과 큰 문서 세트”로 문서화하는 것이다.

[산출물(반드시 파일로 생성/수정)]

1. README.md (프로젝트 소개/실행/구성/링크)
2. docs/api/openapi.yaml (OpenAPI 3.0 스펙) + docs/api/README.md (사용법 요약)
3. docs/erd.md (ERD: Mermaid erDiagram + 핵심 테이블 설명)
4. docs/architecture.md (아키텍처 1장: Mermaid 다이어그램 + 설계 포인트)
5. docs/troubleshooting.md (트러블슈팅 1~2개: 문제→원인→해결→결과)

[작업 규칙]

- 레포의 실제 코드/설정 파일을 근거로 문서를 작성한다. 추측 금지.
- 확실하지 않은 내용은 “TODO(근거 파일/경로/라인 힌트)”로 남긴다.
- 문서에 등장하는 기술/구성요소는 실제 존재하는 것만.
- 모든 예시는 “실제 엔드포인트/DTO/필드명” 기반으로 작성.
- 최종적으로 `docs/INDEX.md`를 만들어 전체 문서 링크를 한 곳에 모아라.

[리포지토리 분석 지시]

- 먼저 다음을 찾아 요약하라:
  - 런타임/프레임워크(예: Spring Boot, Next.js 등), 빌드툴(Gradle/Maven), 실행 커맨드
  - 인증/인가 방식(JWT/OAuth2/세션) 및 권한(Role)
  - 주요 도메인/기능(예: 활동/신청/상태변경 등)
  - DB/캐시/메시지큐/외부 API 연동 여부
- 그 다음 실제 파일을 바탕으로 문서 파일들을 생성/수정하라.

[각 문서별 요구사항]

(1) README.md

- 상단: 한 줄 소개 + 핵심 가치(무엇을 해결?)
- 핵심 기능 5개 이내(불릿)
- 기술 스택(서버/클라/DB/인프라/툴)
- 빠른 시작(로컬 실행): 의존성, 환경변수(.env.example 있으면 활용), 실행 커맨드
- 폴더 구조/모듈 구조(멀티모듈이면 설명)
- 문서 링크 섹션: docs/INDEX.md, OpenAPI, ERD, Architecture, Troubleshooting

(2) OpenAPI

- docs/api/openapi.yaml 을 OpenAPI 3.0으로 작성한다.
- Controller/Router를 기반으로 엔드포인트를 생성하고, Request/Response 스키마는 DTO/스키마 파일을 근거로 만든다.
- 인증 방식은 securitySchemes로 정의하고, 보호 엔드포인트에 security 적용
- 대표 에러 포맷(에러코드/메시지)을 components/schemas로 정의
- docs/api/README.md 에:
  - Base URL, Auth 방법, 예시 curl 3개(로그인/조회/상태변경 같은 대표 흐름)

(3) ERD (docs/erd.md)

- 실제 엔티티/테이블 정의를 근거로 Mermaid erDiagram 작성
- 핵심 테이블 5~12개만(너무 많으면 축약) + 관계 표시
- 아래에 “테이블별 역할”을 1~2줄로 설명
- 인덱스/유니크/소프트삭제/이력 테이블 같은 운영 포인트가 있으면 별도 섹션으로 정리

(4) Architecture (docs/architecture.md)

- “한 장”으로 보이는 Mermaid 다이어그램(노드 10~14개 이내)
  - 사용자/관리자 → 클라이언트(웹/어드민) → API 서버 → DB/캐시/외부API/비동기(있다면)
  - 통신 라벨(HTTPS/REST, async message 등) 표기
- 다이어그램 아래에:
  - 핵심 요청 흐름(로그인 + 대표 기능 1개) 6~10줄
  - 설계 포인트 3~5개(트랜잭션 경계, 캐시, 권한, 실패/재시도, 상태 변경 일관성 등)
  - 운영 포인트 3개(로깅/모니터링/알람/추적)

(5) Troubleshooting (docs/troubleshooting.md)

- 레포에서 실제로 “문제가 됐을 법한 지점”을 근거로 1~2개 작성:
  - 예: 성능 병목, 동시성, N+1, 캐시 일관성, 상태 변경 일괄 처리, 보안 설정 실수 등
- 형식: 문제(증상) → 원인(근거 파일) → 해결(변경 내용) → 결과(정성/정량)
- 정량 수치가 없으면 억지로 만들지 말고 “관측 방법/측정 포인트”를 제안

(6) docs/INDEX.md

- 위 문서로 링크를 걸어 “문서 포털”을 만든다.

[완료 조건]

- 모든 파일이 생성되어 있고, README에서 문서 링크가 동작한다.
- 다이어그램은 Markdown에서 렌더링 가능한 Mermaid 문법이다.
- TODO는 최소화하되, 남길 때는 반드시 근거 파일 경로를 포함한다.

이제 작업을 시작하라.
