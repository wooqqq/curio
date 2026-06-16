# Curio — CLAUDE.md

AI 세션 간 컨텍스트 유지용 문서. 새 세션 시작 시 이 파일부터 읽는다.

---

## 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 서비스명 | Curio |
| 한 줄 설명 | 카카오톡으로 보내기만 하면 AI가 자동 정리해주는 개인 아카이브 |
| 타겟 | IT 취준생 / 주니어 개발자 |
| 도메인 | mycurio.kr (배포 직전 구매 예정) |

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 백엔드 | Java 21, Spring Boot 3.5.x, Gradle |
| DB | MySQL 8 (Docker Compose 로컬) |
| 캐시/큐 | Redis |
| ORM | Hibernate JPA (ddl-auto: update) |
| 인증 | 카카오 OAuth2 + JWT (Access 메모리 / Refresh httpOnly 쿠키) |
| AI | Google Gemini (gemini-2.5-flash, AI Studio 무료티어) |
| 파일 | AWS S3 (키: userId/yyyy/MM/uuid.png) |
| API 문서 | SpringDoc OpenAPI (Swagger) |
| 프론트 | React + Vite, Tailwind CSS, Zustand |
| 배포 | 백엔드: Railway(Docker) / 프론트: Vercel / CI: GitHub Actions |

---

## 디렉토리 구조

```
curio/                          ← 모노레포 루트
├── CLAUDE.md                   ← 이 파일
├── PROGRESS.md                 ← 일별 진행 기록 (gitignore)
├── curio-implementation-plan.md
├── docker-compose.yml
├── .env.example
├── frontend/
│   └── src/
│       ├── components/
│       ├── pages/
│       ├── store/              ← Zustand
│       ├── api/
│       └── styles/
└── backend/
    └── src/main/java/com/curio/
        ├── config/             ← CORS, Swagger, Security, Async, AWS
        ├── dto/
        │   ├── kakao/          ← KakaoSkillRequest/Response
        │   └── item/           ← OgData, ClassificationResult
        ├── entity/
        │   ├── enums/          ← ItemType, Category, ItemStatus
        │   └── (User, Item, Tag, RefreshToken, Announcement, Popup)
        ├── repository/
        ├── service/
        │   ├── queue/          ← QueueService (인터페이스 + 구현체)
        │   ├── OgCrawlerService
        │   ├── OpenAiService
        │   ├── S3Service       ← uploadFromUrl(크롤 이미지) + uploadImage(관리자 업로드, magic byte 검증)
        │   ├── LinkCodeService
        │   ├── AnnouncementService
        │   └── PopupService    ← 활성 팝업 1개 보장
        ├── processor/          ← ItemProcessor (크롤링, AI, 저장)
        ├── controller/         ← Item, Announcement, Popup(공개) + Admin(관리자 전용)
        ├── exception/          ← GlobalExceptionHandler, ErrorCode
        └── security/           ← JwtFilter, JwtUtil, CookieUtil, AdminGuard(ADMIN_KAKAO_IDS allowlist)
```

---

## API 규칙

- prefix: `/api/v1`
- 응답 형식: `{ code, data, message }` (ApiResponse)
- 에러 시 code는 ErrorCode enum 값

---

## 카카오 오픈빌더 연동 핵심

- 챗봇 스킬 서버 **5초 응답 제한** → 즉시 "저장 중" 응답 후 비동기 처리
- 비동기 구조: `Controller → QueueService → ItemProcessor`
- MVP: `@Async` / 추후: Redis Stream 교체 가능 구조

---

## ERD 요약

- `users` — 카카오 OAuth 유저
- `items` — 저장 아이템 (LINK/IMAGE/TEXT, 카테고리: DEVELOPMENT/CAREER/ETC — 커리어·취업 통합)
- `tags` + `item_tags` — 다대다
- `refresh_tokens` — JWT refresh rotation
- `announcements` — 관리자 공지 게시물
- `popups` — 진입 팝업 배너 (활성 1개, linkUrl로 공지/외부 연결)

---

## 환경 변수

루트의 `.env.example` 참고. 실제 값은 `.env`에 저장 (gitignore).

---

## 배포 (운영)

- 백엔드: Railway — `https://curio-production-1728.up.railway.app` (Docker, `/actuator/health`)
- 프론트: Vercel — `https://curio-three-ashy.vercel.app`
- 프론트가 `/api/*`를 Railway로 프록시(`frontend/vercel.json`) → 같은 출처라 refresh 쿠키 first-party
- 카카오봇 스킬 URL = Railway `/api/v1/kakao/skill` (ngrok 불필요)
- 배포 절차·환경변수 체크리스트는 루트 `DEPLOY.md`
- 브랜치 흐름: `develop` 작업 → `main` 머지 = 자동 배포 (Railway/Vercel이 main 추적), CI는 GitHub Actions

---

## 구현 단계 현황

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 환경 세팅 | ✅ 완료 |
| Phase 2 | 카카오 로그인 | ✅ 완료 |
| Phase 3 | 핵심 파이프라인 | ✅ 완료 |
| Phase 4 | 웹 아카이브 UI | ✅ 완료 |
| Phase 5 | 검색 | ✅ 완료 |
| Phase 6 | 배포 | ✅ 완료 (Railway + Vercel, 로그인·저장 E2E 검증) |

### 배포 후 추가 기능 (✅ 배포 완료)
- 공지/팝업 관리자 페이지 (`/admin`) — 공지 CRUD, 팝업(이미지 업로드·linkUrl·활성) CRUD
- 사용자 공지 열람 — `/announcements` 목록 + 상세, 아카이브 진입 팝업 모달(이미지/본문 클릭→linkUrl, 오늘 하루 보지 않기)
- 관리자 동선: 팝업 폼에서 "공지에서 가져오기"로 linkUrl 자동입력 / 공지 상세에 관리자용 "팝업 만들기"(linkUrl 프리필)·수정·삭제 (쿼리파라미터 `?tab=&link=&edit=`)
- 관리자 권한: `ADMIN_KAKAO_IDS` allowlist (설계결정 #15) / 팝업 모델: 설계결정 #16
- S3 실구축 완료 (버킷 `curio-prod-assets`, 서울) — 그 전까지 dormant였음
- 웹에서 링크 직접 추가 — 아카이브 우하단 FAB(+) → 바텀시트, `POST /items` 동기 처리(크롤+분류+저장 후 아이템 반환, 중복 시 `DUPLICATE_URL`), 봇과 저장 로직 공유 (설계결정 #17)
- 유튜브 링크 oEmbed — 유튜브 호스트는 oEmbed로 제목·썸네일·채널명 (일반 스크래핑 타임아웃 회피), 실패 시 OG 크롤 폴백 (설계결정 #18)
- AI 분류 Gemini 전환 — OpenAI(크레딧 소진)에서 Google Gemini 무료티어로 교체 (`GeminiService`, gemini-2.5-flash). 프롬프트·파이프라인 동일 (설계결정 #19)
- 카테고리 커리어·취업 통합 — `Category`에서 JOB 제거, DEVELOPMENT/CAREER/ETC 3개. 세부는 태그가 담당 (설계결정 #20). AI 호출 실패 시 ETC 대신 미분류(null)로 남겨 오염 방지 (설계결정 #21)
- 휴면 `aiSummary` 필드 제거 — 항상 null이던 죽은 필드(엔티티·DTO·검색·프론트) 정리 (설계결정 #22)
- URL 중복 정규화 개선 — `normalizeUrl`이 쿼리를 통째로 버려 유튜브 영상이 중복 판정되던 버그 수정. 추적 파라미터(`utm_*`·`fbclid` 등)만 제거(denylist)하고 의미 있는 쿼리 보존 (설계결정 #23)

### 테스트 / 문서
- 단위·슬라이스 테스트 1~3단계 도입 — 1단계 순수 함수(`detectType`·`normalizeUrl`·`titleFromUrl`·`truncate`) + 2단계 목킹(Mockito: `ItemClassifier`·`ItemProcessor`) + 3단계 슬라이스(`@WebMvcTest ItemController` 실제 SecurityConfig import / `@DataJpaTest ItemRepository.search` H2 MySQL 호환 모드, 페이징·COUNT DISTINCT 포함). 10클래스 ~70케이스. 테스트가 실제 버그 4건 발견·수정(유튜브 URL 중복 정규화 / AI 태그 중복 / 봇 발화 URL 미추출 / 미인증 응답 403→401 — 설계결정 #24). 전략·로드맵은 `docs/testing.md`, 슬라이스 전용 프로필은 `application-test.yaml`
- 예측 버그 백로그 — 3단계 후 코드 정독으로 추린 잠재 버그 5건(PROGRESS에 정리). #1 페이지네이션 음수/0→500, #4 링크 동시추가 TOCTOU→500(설계결정 #25), #2 필드 길이 초과→500(저장 전 truncate + 이모지 surrogate 반토막 방지, 설계결정 #26)은 수정·배포 완료. #3 SSRF / #5 동기 크롤 스레드 점유는 남음
- 기능 명세서 — 노션 프로젝트 페이지 아래 인라인 DB(코드 기반 19개 기능, 대/중/소 계층번호)

---

## 세션 시작 시

새 세션 시작 시 CLAUDE.md → PROGRESS.md 순으로 읽고 컨텍스트 파악.
