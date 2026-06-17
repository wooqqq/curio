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
- `h2`(testRuntimeOnly) — `@DataJpaTest`용 임베디드 DB (MySQL 호환 모드)
- `src/test/resources/application-test.yaml` — 슬라이스 전용 프로필(`@ActiveProfiles("test")`). dev의 MySQL·카카오 설정을 끌어오지 않게 분리
- CI(`.github/workflows/ci.yml`)가 MySQL·Redis 컨테이너로 `./gradlew test`를 실행 → **테스트를 추가하면 자동으로 머지 게이트가 된다.**

## 계층 (테스트 피라미드) — 1·2·3단계 모두 도입 완료

현재 11클래스 ~85케이스. `contextLoads`(`@SpringBootTest`)는 MySQL·Redis가 필요해 CI에서만 돈다.

### 1단계 — 단위 (순수 함수, DB·Spring 무관, ms 단위)
가장 빠르고 ROI가 높다. `private` 메서드는 같은 패키지 테스트에서 부르려고 **package-private**로 열었다.
- `ItemProcessorTest` — `detectType`(LINK/IMAGE/TEXT 분기), `normalizeUrl`(중복 판정용 정규화 + 추적 파라미터 제거)
- `OgCrawlerServiceTest` — `titleFromUrl`(크롤 실패 시 슬러그 디코딩, 유튜브 `watch` 약점 명시)
- `GeminiServiceTest` — `parseResult`(태그 dedup·정상 파싱)
- `TextUtilsTest` — `truncate`(null 안전·길이 컷·이모지 surrogate 반토막 방지, 설계결정 #26)
- `ItemLengthTest` — `Item` 빌더/`updateLinkMetadata`·`Tag` 생성자가 컬럼 길이로 자르는지(설계결정 #26)
- `UrlGuardTest` — `verifyPublic`(SSRF: 내부/사설/비http 차단·공개 IPv4/IPv6 통과, 설계결정 #27)
- `OgCrawlerServiceTest` — `crawl`이 fetch 전 내부/비http를 `BLOCKED_URL`로 막는지(설계결정 #27)

### 2단계 — 의존성 목킹 (Mockito)
설계 결정을 코드에서 잠근다.
- `ItemClassifierTest` — Gemini 실패 시 `category`가 `null`로 남는지(설계결정 #21), 이미지·짧은 텍스트 ETC, 키 비활성 미분류, 긴 태그명 50자 컷(설계결정 #26)
- `ItemProcessorAddLinkTest` — 중복 URL `DUPLICATE_URL`(설계결정 #17), URL 아님 `INVALID_INPUT`, 동시 추가 제약 위반 → `DUPLICATE_URL`
- `ItemProcessorProcessTest` — 봇 발화에 텍스트가 섞여도 URL만 추출

### 3단계 — 웹 / JPA 슬라이스
- `ItemControllerTest`(`@WebMvcTest`) — 실제 `SecurityConfig`·`CorsConfig`를 import해 인가를 그대로 태운다. 미인증 401(설계결정 #24), 남의 아이템 삭제 `ITEM_NOT_FOUND`(404), 중복 409, 입력 400, 페이지 파라미터 보정
- `ItemRepositorySearchTest`(`@DataJpaTest`) — H2(MySQL 호환 모드). `search`의 LIKE·카테고리 필터·유저 격리·DISTINCT·정렬·다중 페이지·`COUNT DISTINCT`. MySQL 의존 동작이 걸리면 Testcontainers-MySQL로 승격 여지

### 인증 핵심 경로 — 보안 단위 테스트
슬라이스(`@WebMvcTest`)는 `authentication()` 포스트프로세서로 필터를 우회하므로, 토큰→principal 경로는 직접 테스트로 따로 고정한다.
- `JwtUtilTest` — access/refresh 발급→userId 복원, **키 격리**(access를 refresh 키로 검증 시 실패, 역도), 변조·비JWT·만료 토큰 검증 실패, 같은 유저 연속 발급 시 **jti로 토큰 비중복**(설계결정 #11 회귀 방어)
- `JwtFilterTest` — 유효 Bearer 토큰이면 `SecurityContext`에 userId를 principal로 주입, 헤더 없음/비Bearer/무효/만료면 인증하지 않음(체인은 항상 진행 → 이후 401은 `JwtAuthenticationEntryPoint` 담당, #24)

## 컨벤션

- 파일명: `<대상>Test` (예: `ItemProcessorTest`)
- 메서드명: 한글 서술형으로 의도를 드러낸다 (`detectType_이미지URL은_IMAGE`)
- 표로 떨어지는 입력은 `@ParameterizedTest` + `@CsvSource`
- 단위 테스트는 Spring 컨텍스트를 띄우지 않는다(`new`로 직접 생성)

## 회귀 박제 목록 (실제 겪은 버그 → 테스트로 고정)

**(가) 테스트 도입 *전에* 겪고, 도입하며 함께 고정한 것**
- 유튜브 링크 제목이 `watch`로 깨짐 → `titleFromUrl` / oEmbed 분기 (설계결정 #18)
- Medium 링크가 raw URL로 깨짐 → `titleFromUrl` 폴백
- AI 429 실패가 `ETC`로 오염 → 이제 `null` 유지 (설계결정 #21)

**(나) 테스트를 짜다가 *새로 발견*해 고친 것** — "green ≠ 버그 없음"의 증거
- URL 정규화가 쿼리를 통째로 버려 서로 다른 유튜브 영상이 중복 판정 → 추적 파라미터만 제거 (설계결정 #23)
- Gemini가 같은 태그를 여러 번 반환하면 중복 누적 → `parseResult`에서 dedup
- 봇 발화에 텍스트가 섞이면 URL을 안 뽑고 통째로 크롤 → `extractUrl`로 통일
- 미인증 응답이 403이라 프론트 토큰 재발급이 안 됨 → 401 반환 (설계결정 #24)
- 링크 동시 추가(TOCTOU) 시 제약 위반이 500 → `DUPLICATE_URL`로 매핑
- 크롤 제목·태그·URL이 컬럼 길이를 넘으면 strict 모드 insert가 500 → 저장 전 truncate (예측 버그 #2, 설계결정 #26)
- 그 truncate가 이모지(surrogate pair)를 반토막 내 외톨이 surrogate가 utf8mb4 저장을 깨뜨림 → 깨진 반쪽까지 버림 (설계결정 #26)

**(다) 코드 정독으로 예측해 선제 차단한 것** (예측 버그 백로그)
- 임의 URL을 서버가 fetch해 내부 자원에 닿는 SSRF → fetch 전 IP 대역 검증, 리다이렉트 매 홉 재검증 (예측 버그 #3, 설계결정 #27)

## 실행

```bash
cd backend
./gradlew test
```
