# Curio — 프론트엔드

Curio 웹 아카이브의 프론트엔드 (React + Vite + Tailwind CSS + Zustand).
프로젝트 전체 개요·기술 스택·설계 결정은 [루트 README](../README.md)와 [CLAUDE.md](../CLAUDE.md)를 참고하세요.

## 개발

```bash
npm install
npm run dev      # http://localhost:5173 — /api 는 localhost:8080(백엔드)로 프록시
npm run build    # 프로덕션 빌드 → dist/
```

## 구조

```
src/
├── api/         # axios 클라이언트 + 엔드포인트별 모듈
├── pages/       # 라우트 단위 페이지 (Archive, Login, Admin, Announcement…)
├── components/  # 공용 컴포넌트 (PopupModal 등)
└── store/       # Zustand (인증 상태)
```
