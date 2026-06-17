# Curio

카카오톡으로 링크·이미지·텍스트를 보내기만 하면 AI가 자동으로 정리해주는 개인 아카이브.

🔗 **라이브 데모(베타)**: https://curio-three-ashy.vercel.app

읽다가 "나중에 봐야지" 하고 흘려보내는 글들을, 따로 앱을 켜지 않고 평소 쓰던 카카오톡 채널에 보내는 것만으로 모아두고 다시 찾아볼 수 있게 만드는 게 목표다. 타겟은 레퍼런스를 자주 모으는 IT 취준생·주니어 개발자.

## 핵심 기능

- **카카오톡으로 저장** — 오픈빌더 챗봇 채널에 링크/이미지/텍스트를 보내면 저장된다. 사진은 카카오가 주는 이미지 URL을 받아 S3에 보관한다.
- **AI 자동 분류** — Google Gemini로 카테고리(개발 / 커리어·취업 / 기타) 분류 + 키워드 태그.
- **링크 메타데이터 수집** — OG 태그로 제목·썸네일·설명을 긁어온다(유튜브는 oEmbed). 실패 시 폴백·재크롤로 복구.
- **웹에서 직접 추가** — 봇 없이 웹 아카이브에서 링크를 붙여넣어 바로 저장(우하단 + 버튼).
- **웹 아카이브** — 카드형 피드, 카테고리 필터, 키워드 검색(제목/본문/태그).
- **공지·팝업** — 관리자가 공지를 올리고, 아카이브 진입 시 팝업으로 안내(관리자 페이지 + 진입 팝업, 팝업→공지 연결).

## 기술 스택

| 구분 | 기술 |
|------|------|
| 백엔드 | Java 21, Spring Boot 3.5, Gradle |
| DB / 캐시 | MySQL 8, Redis |
| 인증 | 카카오 OAuth2 + JWT (Access=메모리 / Refresh=httpOnly 쿠키) |
| AI | Google Gemini (gemini-2.5-flash, 무료티어) |
| 파일 | AWS S3 |
| 프론트 | React + Vite, Tailwind CSS, Zustand |
| 배포 | 백엔드 Railway(Docker) · 프론트 Vercel · CI GitHub Actions |

## 동작 개요

카카오 스킬 서버는 5초 안에 응답해야 해서, 요청을 받으면 즉시 "저장 중" 응답을 돌려주고 실제 처리(크롤링·AI 분류·S3 업로드)는 비동기로 돌린다.

```
카카오톡 → KakaoController → QueueService → ItemProcessor
                 (즉시 응답)        (비동기)   (크롤링·AI·저장)
```

설계 결정의 배경과 트레이드오프는 [docs/architecture-decisions.md](docs/architecture-decisions.md)에 정리했다.

## 로컬 실행

```bash
# 1. 인프라 (MySQL + Redis)
docker-compose up -d

# 2. 백엔드 (Java 21 필요)
cd backend && ./gradlew bootRun

# 3. 프론트
cd frontend && npm install && npm run dev
```

환경 변수는 루트 `.env.example` 참고.

## AI 협업 방식

이 프로젝트는 Claude Code를 보조 도구로 쓰되, **일회성 프롬프트가 아니라 재현 가능한 워크플로우로** 구성해 진행했다.

- **`CLAUDE.md`** — 프로젝트 컨텍스트(스택·구조·규칙·진행 단계)를 한 파일에 모아, 새 세션마다 AI가 같은 맥락에서 시작하도록 했다.
- **`.claude/skills/`** — 반복 작업을 스킬로 자동화. 예: `sync-docs`는 문서(CLAUDE.md / PROGRESS / 설계 결정 / 노션)를 한 번에 갱신한다.
- **`docs/architecture-decisions.md`** — "왜 이렇게 짰는지"를 결정 단위로 기록. 코드만으로는 안 보이는 판단 근거를 남긴다.
- **테스트·코드리뷰 루프** — 단위·슬라이스 테스트(14클래스 ~105케이스)와 `/code-review`로 머지 전에 검증한다. AI가 만든 변경을 사람이 검증 루프로 거르는 쪽에 무게를 둔다(테스트가 실제 버그 4건을 잡았다). 전략은 [docs/testing.md](docs/testing.md).
- **TIL** — 개발 중 막혔다 배운 것(예: Tailwind v4 cascade layers 함정, OG 크롤링 폴백)을 따로 정리해 블로그 글의 씨앗으로 쓴다.

목적은 AI에 코드를 맡기는 게 아니라, 맥락·결정·반복작업을 구조화해서 사람이 판단에 집중하는 것.
