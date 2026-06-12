# Curio 테스트 전략

테스트를 "왜·무엇을·어떻게" 쌓는지 정리한 문서. 구현 현황은 `CLAUDE.md`, 설계 이유는 `architecture-decisions.md`를 본다.

---

## 목표

1. **회귀 방지** — 이미 고친 버그를 테스트로 박제해 다시 안 터지게 한다.
2. **핵심 로직 신뢰** — 저장 파이프라인(타입 감지·중복·분류)과 인증을 검증한다.
3. **머지 게이트** — CI에서 자동으로 돌아 깨진 변경을 막는다.

## 토대 (이미 갖춰진 것)

- `spring-boot-starter-test` — JUnit 5 + Mockito + AssertJ
- `spring-security-test` — 인증 슬라이스 테스트용
- CI(`.github/workflows/ci.yml`)가 MySQL·Redis 컨테이너로 `./gradlew test`를 실행 → **테스트를 추가하면 자동으로 머지 게이트가 된다.**

별도 라이브러리 추가 없이 단위·슬라이스 테스트를 바로 쓸 수 있다.

## 계층 (테스트 피라미드)

### 1단계 — 단위 (순수 함수, DB·Spring 무관, ms 단위)
가장 빠르고 ROI가 높다. 우선 타겟:
- `ItemProcessor.detectType` — LINK / IMAGE / TEXT 분기 (정규식)
- `ItemProcessor.normalizeUrl` — 중복 판정용 URL 정규화 (host 소문자, 끝 슬래시 제거)
- `OgCrawlerService.titleFromUrl` — 크롤 실패 시 슬러그 디코딩 (유튜브 `watch` 깨짐 회귀)

> `detectType`·`normalizeUrl`은 현재 `private`. 테스트를 위해 **package-private**로 연다(같은 패키지 테스트에서 호출).

### 2단계 — 의존성 목킹 (Mockito)
설계 결정을 코드에서 잠근다.
- `ItemClassifier` — Gemini 호출 실패 시 `category`가 `null`로 남는지 (설계결정 #21 회귀 방어)
- `ItemProcessor.addLink` — 중복 URL이면 `DUPLICATE_URL` 예외 (설계결정 #17)

### 3단계 — 웹 / JPA 슬라이스
- `@WebMvcTest` + `spring-security-test` — `ItemController`: 미인증 401, 남의 아이템 삭제 시 `ITEM_NOT_FOUND`
- `@DataJpaTest` — `ItemRepository.search`: 제목·본문·태그 LIKE. H2로 시작하고, MySQL 의존 동작이 걸리면 Testcontainers-MySQL로 승격

## 컨벤션

- 파일명: `<대상>Test` (예: `ItemProcessorTest`)
- 메서드명: 한글 서술형으로 의도를 드러낸다 (`detectType_이미지URL은_IMAGE`)
- 표로 떨어지는 입력은 `@ParameterizedTest` + `@CsvSource`
- 단위 테스트는 Spring 컨텍스트를 띄우지 않는다(`new`로 직접 생성)

## 회귀 박제 목록 (실제 겪은 버그 → 테스트로 고정)

- 유튜브 링크 제목이 `watch`로 깨짐 → `titleFromUrl` / oEmbed 분기 (설계결정 #18)
- Medium 링크가 raw URL로 깨짐 → `titleFromUrl` 폴백
- AI 429 실패가 `ETC`로 오염 → 이제 `null` 유지 (설계결정 #21)

## 실행

```bash
cd backend
./gradlew test
```
