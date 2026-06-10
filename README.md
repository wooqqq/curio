# Curio

카카오톡으로 링크·이미지·텍스트를 보내기만 하면 AI가 자동으로 정리해주는 개인 아카이브.

🔗 **라이브 데모(베타)**: https://curio-three-ashy.vercel.app

읽다가 "나중에 봐야지" 하고 흘려보내는 글들을, 따로 앱을 켜지 않고 평소 쓰던 카카오톡 채널에 보내는 것만으로 모아두고 다시 찾아볼 수 있게 만드는 게 목표다. 타겟은 레퍼런스를 자주 모으는 IT 취준생·주니어 개발자.

## 핵심 기능

- **카카오톡으로 저장** — 오픈빌더 챗봇 채널에 링크/이미지/텍스트를 보내면 저장된다.
- **AI 자동 정리** — OpenAI로 카테고리(개발/취업/커리어/기타) 분류 + 태그 + 요약 생성.
- **링크 메타데이터 수집** — OG 태그로 제목·썸네일·설명을 긁어온다. 실패 시 폴백·재크롤로 복구.
- **웹 아카이브** — 카드형 피드, 카테고리 필터, 키워드 검색(제목/본문/요약/태그).
- **공지·팝업** — 관리자가 공지를 올리고, 아카이브 진입 시 팝업으로 안내(관리자 페이지 + 진입 팝업, 팝업→공지 연결).

## 기술 스택

| 구분 | 기술 |
|------|------|
| 백엔드 | Java 21, Spring Boot 3.5, Gradle |
| DB / 캐시 | MySQL 8, Redis |
| 인증 | 카카오 OAuth2 + JWT (Access=메모리 / Refresh=httpOnly 쿠키) |
| AI | OpenAI gpt-4.1-mini |
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
- **TIL** — 개발 중 막혔다 배운 것(예: Tailwind v4 cascade layers 함정, OG 크롤링 폴백)을 따로 정리해 블로그 글의 씨앗으로 쓴다.

목적은 AI에 코드를 맡기는 게 아니라, 맥락·결정·반복작업을 구조화해서 사람이 판단에 집중하는 것.
