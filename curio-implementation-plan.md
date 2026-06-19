# 구현 계획서 — Curio

구현 전 검토용 문서. 확인 후 이 계획에 따라 단계별로 구현한다.

> 이 문서는 MVP 시점(2026-05)의 초기 청사진이다. 본문은 그대로 두어 *원안 ↔ 현재* 대조 자료로 남긴다. 이후 기획·구현이 바뀐 부분은 본문을 고치지 않고 아래 "변경 이력" 표로 추적하며, 각 변경의 상세 근거는 `docs/architecture-decisions.md`의 설계결정 #N에 있다. (보안·견고화성 결정 #23~#29 등 계획 방향과 무관한 항목은 표에서 생략 — 설계결정 문서 참조.)

---

## 0. 변경 이력 (초기 계획 → 현재)

| 영역 | 초기 계획(본 문서) | 현재 | 설계결정 |
|------|------|------|------|
| AI 공급자 | OpenAI (gpt-4.1-mini) | Google Gemini 무료티어 (gemini-2.5-flash) | #19 |
| 자동 카테고리 | 개발/취업/커리어/기타 4종 고정 | 개발/커리어(취업 통합)/기타 3종, 세부는 태그 | #20 |
| AI 분류 실패 | (암묵적으로 ETC) | 미분류(null)로 남기고 reclassify-all 백필 | #21 |
| AI 요약 | aiSummary 한 줄 요약 | 미구현·필드 제거 | #22 |
| 링크 수집 경로 | 봇 중심(웹 직접 저장은 추후) | 웹 직접 추가(동기) + 봇(비동기) 병행 | #17 |
| 유튜브 링크 | 일반 OG 크롤 | oEmbed 우선, 실패 시 크롤 폴백 | #18 |
| 사진 첨부 | (입력 경로 미정 — S3 URL 저장만) | 카톡 media.url을 IMAGE로 명시 처리 | #30 |
| 사진 제목·분류 | (없음) | 날짜형 기본 → AI 비전 캡션+분류로 개선 | #31·#32 |

---

## 1. 서비스 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | Curio |
| **한 줄 설명** | 카카오톡으로 보내기만 하면 AI가 자동 정리해주는 개인 아카이브 |
| **타겟** | IT 취준생 / 주니어 개발자 (추후 일반 유저 확장 가능) |
| **핵심 가치** | 노션처럼 정리되는데, 카톡으로 넣기만 하면 되는 서비스 |

---

## 2. 결정 사항 요약

| 구분 | 항목 | 결정 |
|------|------|------|
| **입력** | 주요 수집 경로 | 카카오톡 오픈빌더 챗봇 (MVP 핵심) |
| | 추가 수집 경로 | 웹 직접 저장, 크롬 익스텐션, 모바일 공유 (추후) |
| **처리** | AI 분류 | OpenAI API (gpt-4.1-mini) |
| | AI 비용 방어 | 규칙 기반 필터 먼저 → AI 호출 (URL 포함 여부, 텍스트 길이 등) |
| | 자동 카테고리 | 개발 / 취업 / 커리어 / 기타 (4종 고정, MVP) |
| | 비동기 처리 | QueueService 계층 분리 → MVP는 @Async, 추후 Redis Stream 교체 가능 구조 |
| | 이미지 | S3 업로드 후 URL 저장 (키 전략: userId/yyyy/MM/uuid.png) |
| | 링크 | OG 태그 크롤링 후 제목/썸네일 파싱 + 중복 체크 (user_id + normalized_url) |
| **출력** | 조회 | 웹 아카이브 (모바일 우선) |
| | 검색 | title + tags 키워드 검색 (MVP), 자연어 검색 (추후) |
| | 태그 | AI 자동 태그 + 웹에서 사용자 수동 추가/수정 |
| **인증** | 로그인 | 카카오 OAuth2 로그인 |
| | JWT | Access 토큰 (짧은 만료) + Refresh 토큰 DB 저장, 1회 사용(rotation) |
| **백엔드** | 언어/프레임워크 | Java 21 + Spring Boot 3.x |
| | 빌드 도구 | Gradle |
| | DB | MySQL 8 (Docker Compose 로컬 실행) |
| | ORM | Hibernate JPA (ddl-auto: update) |
| | 캐시/큐 | Redis (비동기 작업 큐, 세션 관리) |
| | 파일 저장 | AWS S3 |
| | API prefix | `/api/v1` |
| | API 문서 | Swagger (SpringDoc OpenAPI) |
| | 에러 응답 | `{ code, data, message }` 고정 |
| | 환경 | dev / prod 2종 프로필 |
| **프론트** | 프레임워크 | React (+ Vite) |
| | 스타일 | Tailwind CSS |
| | 상태관리 | Zustand |
| | 모바일 | 모바일 웹 우선 (반응형) |
| **배포** | 백엔드 | AWS EC2 또는 Railway |
| | 프론트 | Vercel |
| **기타** | 다국어 | 없음 (한국어만) |
| | 알림 | 추후 검토 (PWA 푸시 또는 앱) |

---

## 3. 카카오 오픈빌더 연동 — 핵심 주의사항

### 3.1 5초 응답 제한 처리 (필수)

카카오 챗봇 스킬 서버는 **5초 이내 응답**을 요구한다.
URL 크롤링 + AI 분류는 5초 안에 완료되지 않으므로 아래 구조로 처리한다.

```
유저가 카톡으로 링크 전송
       ↓
Controller 수신
       ↓
즉시 응답: "📌 저장 중이에요!" (5초 이내)
       ↓
QueueService.enqueue() 호출
       ↓
ItemProcessor 백그라운드 처리 (MVP: @Async / 추후: Redis Stream)
 ├── 규칙 기반 필터 (URL 여부, 텍스트 길이 등)
 ├── 링크 → OG 태그 크롤링 + 중복 체크
 ├── 이미지 → S3 업로드 (userId/yyyy/MM/uuid.png)
 └── 텍스트 → 그대로 저장
       ↓
AI API 호출 (필터 통과한 경우만) → 카테고리 + 태그 분류
       ↓
MySQL 저장 완료
       ↓
카카오 Event API로 완료 알림
"✅ [개발] 카테고리로 저장됐어요!"
```

### 3.2 비동기 처리 계층 구조

```
Controller
    ↓
QueueService (인터페이스)
    ↓
ItemProcessor
```

- MVP: `QueueService` 내부에서 `@Async` 사용
- 추후 교체 가능: Redis Stream / RabbitMQ / Kafka
- Controller는 QueueService만 바라보므로 교체 시 Controller 수정 불필요

### 3.3 AI 비용 방어 — 규칙 기반 필터

```
입력 수신
    ↓
규칙 기반 필터
 ├── URL 포함? → 링크 처리
 ├── 이미지? → 이미지 처리
 └── 텍스트 길이 짧음(20자 이하)? → AI 스킵, "기타"로 저장
    ↓
필터 통과 시만 OpenAI API 호출
```

### 3.4 플러스친구 vs 오픈빌더

**오픈빌더 챗봇 선택** (이유: 유저 발화 → 스킬 서버 웹훅 구조가 명확하고 자유도 높음)

---

## 4. ERD 초안

```
users
 ├── id (PK)
 ├── kakao_id (카카오 OAuth uid)
 ├── nickname
 ├── profile_image_url
 ├── created_at
 └── updated_at

items (저장된 아이템)
 ├── id (PK)
 ├── user_id (FK → users)
 ├── type (LINK / IMAGE / TEXT)
 ├── title
 ├── content (텍스트 또는 URL)
 ├── thumbnail_url
 ├── original_url
 ├── normalized_url (중복 체크용, user_id + normalized_url UNIQUE)
 ├── s3_key (이미지인 경우, 형식: userId/yyyy/MM/uuid.png)
 ├── category (DEVELOPMENT / CAREER / JOB / ETC)
 ├── status (UNREAD / READ / BOOKMARKED) ← enum, 기본 UNREAD
 ├── ai_summary (AI 요약, 추후)
 ├── created_at
 └── updated_at
 
 * TODO (추후 분리 고려)
 * item_metadata — 원본 메타데이터 분리
 * item_ai_analysis — OCR, 임베딩, 요약 등 AI 분석 결과 분리

tags
 ├── id (PK)
 └── name

item_tags (다대다 연결)
 ├── item_id (FK → items)
 └── tag_id (FK → tags)

refresh_tokens
 ├── id (PK)
 ├── user_id (FK → users)
 ├── token
 ├── expired_at
 └── created_at
```

---

## 5. MVP 기능 범위

### Phase 1 (MVP 핵심)
- 카카오 로그인
- 카카오 오픈빌더 챗봇 연동 (링크/이미지/텍스트 수신)
- AI 자동 카테고리 분류 (규칙 기반 필터 선처리)
- 링크 중복 저장 방지 (user_id + normalized_url)
- 웹 아카이브 조회 (카드 UI, 카테고리 필터)
- 태그 수동 추가/수정
- title + tags 키워드 검색
- 읽음 상태 관리 (UNREAD / READ / BOOKMARKED)

### Phase 2 (MVP 이후)
- AI 요약
- OCR (이미지에서 텍스트 추출)
- 자연어 검색 (pgvector 또는 외부 벡터 DB)
- 웹 직접 저장 / 크롬 익스텐션
- 추천 시스템
- PWA 푸시 알림
- Redis Stream 전환 (트래픽 증가 시)
- item_metadata / item_ai_analysis 테이블 분리 (데이터 증가 시)

---

## 6. 구현 순서

### Phase 1 — 환경 세팅 (1~2일)
- GitHub 레포 생성 (모노레포 — frontend + backend 한 레포)
- Spring Boot 프로젝트 초기화 (Java 21, Gradle)
- React + Vite 프로젝트 초기화
- Docker Compose: MySQL + Redis 로컬 환경 세팅
- 공통 코드: ApiResponse, GlobalExceptionHandler, Swagger, CORS

### Phase 2 — 카카오 로그인 (2~3일)
- 카카오 디벨로퍼스 앱 등록 ✅ 완료
- Spring Security + OAuth2 카카오 로그인
- JWT Access/Refresh 발급 및 프론트 연동
- 로그인/로그아웃 화면

### Phase 3 — 핵심 파이프라인 (1주일)
- 카카오 오픈빌더 챗봇 생성 + 스킬 서버 연동
- QueueService 계층 구현 (Controller → QueueService → ItemProcessor)
- 규칙 기반 필터 구현
- URL 크롤링 (OG 태그 파싱) + 중복 체크
- S3 이미지 업로드 (userId/yyyy/MM/uuid.png)
- AI 분류 API 연동 (필터 통과 시만 호출)
- MySQL 저장

### Phase 4 — 웹 아카이브 UI (1주일)
- 아이템 리스트 조회 (카드 UI)
- 카테고리 필터
- 태그 추가/수정
- 읽음 상태 관리 (UNREAD / READ / BOOKMARKED)
- 모바일 반응형

### Phase 5 — 검색 (3~4일)
- title + tags 키워드 검색
- 검색 결과 UI

### Phase 6 — 배포 (2~3일)
- 백엔드: AWS EC2 or Railway
- 프론트: Vercel
- S3 버킷 세팅
- 도메인 연결 (mycurio.kr 구매 후)

**예상 총 기간**: 주말 + 퇴근 후 기준 6~8주

---

## 7. 프로젝트 구조

```
(프로젝트 루트)
├── docker-compose.yml       # MySQL + Redis
├── .env                     # 환경 변수 (gitignore)
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── store/           # Zustand
│   │   ├── api/
│   │   └── styles/
│   └── ...
└── backend/
    ├── build.gradle
    ├── Dockerfile
    └── src/
        └── main/java/com/curio/
            ├── config/          # CORS, Swagger, Security
            ├── dto/             # ApiResponse, PaginationDto
            ├── entity/          # User, Item, Tag, RefreshToken
            ├── repository/
            ├── service/
            │   └── queue/       # QueueService (인터페이스 + 구현체)
            ├── processor/       # ItemProcessor (크롤링, AI 분류, 저장)
            ├── controller/
            ├── exception/       # GlobalExceptionHandler
            └── security/        # JwtFilter, JwtUtil
```

---

## 8. 환경 변수 목록

```
# DB
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=
MYSQL_USER=
MYSQL_PASSWORD=

# Redis
REDIS_HOST=
REDIS_PORT=

# JWT
JWT_ACCESS_SECRET=
JWT_REFRESH_SECRET=
JWT_ACCESS_EXPIRATION=
JWT_REFRESH_EXPIRATION=

# Kakao OAuth
KAKAO_CLIENT_ID=
KAKAO_REDIRECT_URI=

# AWS S3
AWS_ACCESS_KEY=
AWS_SECRET_KEY=
AWS_REGION=
S3_BUCKET_NAME=

# AI
OPENAI_API_KEY=

# CORS
CORS_ALLOWED_ORIGINS=

# Spring
SPRING_PROFILES_ACTIVE=dev
```

---

## 9. 확인 체크리스트

- [x] 서비스명 결정 → **Curio**
- [x] AI API 선택 확정 → **OpenAI gpt-4.1-mini**
- [x] GitHub 레포 생성 방식 결정 → **모노레포**
- [x] 도메인 결정 → **mycurio.kr** (배포 직전 구매 예정)
- [x] AWS 계정 생성 완료
- [x] 카카오 디벨로퍼스 Curio 앱 등록 완료
- [ ] OpenAI 계정 생성 및 API 키 발급
- [ ] GitHub 레포 생성

---

## 10. 내일 시작 전 할 일 (Claude Code 전에)

1. **OpenAI 계정 생성 + API 키 발급** → https://platform.openai.com/
   - 가입 후 API Keys 메뉴에서 키 발급
   - 발급된 키는 .env 파일에 저장

2. **GitHub 레포 생성**
   - 모노레포 구조로 생성
   - frontend / backend 폴더 분리

---

## 11. 제거/간소화 후보

| 항목 | 설명 | 결정 |
|------|------|------|
| **Next.js** | SSR 필요성 낮음 (로그인 후 개인 아카이브 구조) | React + Vite로 시작, 추후 필요 시 마이그레이션 |
| **PostgreSQL** | pgvector 자연어 검색에 유리 | MVP는 MySQL, 자연어 검색 추가 시 재검토 |
| **앱(네이티브)** | 개발 비용 큼 | MVP 이후 PWA 또는 네이티브 검토 |
| **결제** | MVP 범위 아님 | 상용화 결정 후 추가 |
| **OCR** | 구현 복잡도 높음 | Phase 2 이후 |
| **추천 시스템** | 데이터 충분히 쌓인 후 의미 있음 | Phase 2 이후 |
| **Redis Stream** | @Async로 MVP 충분 | QueueService 구조로 추후 교체 가능하게 설계 |
| **item_metadata 분리** | 현재 items 테이블로 충분 | 데이터 증가 시 분리 검토 (TODO 주석 표기) |
