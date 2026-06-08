# Curio 배포 가이드

> 백엔드: **Railway** (Spring Boot + MySQL + Redis) / 프론트: **Vercel** (React)
> 쿠키 전략: Vercel이 `/api/*`를 Railway로 프록시 → 브라우저엔 same-origin → refresh 쿠키 first-party(SameSite=Lax)

배포 순서: **① 백엔드(Railway) → ② 백엔드 URL 확보 → ③ 프론트(Vercel) → ④ 카카오/CORS 후속 연결 → ⑤ develop→main 머지**

---

## 1. 백엔드 — Railway

1. [railway.app](https://railway.app) 가입 → **New Project → Deploy from GitHub repo** → `wooqqq/curio` 선택
2. 서비스 설정:
   - **Root Directory**: `backend`  (Dockerfile 자동 감지됨)
   - **Health Check Path**: `/actuator/health`  (롤링 배포용 — 새 컨테이너가 200 줄 때까지 트래픽 안 넘김)
3. **+ New → Database → MySQL** 추가
4. **+ New → Database → Redis** 추가
5. 아래 **환경변수**를 백엔드 서비스 Variables 탭에 입력
6. 배포 후 생성된 도메인 확인 (예: `curio-backend-production.up.railway.app`) → **③에서 사용**

### 백엔드 환경변수 체크리스트

| 변수 | 값 | 필수 |
|------|-----|:---:|
| `SPRING_PROFILES_ACTIVE` | `prod` | ✅ |
| `MYSQL_HOST` | Railway MySQL 참조: `${{MySQL.MYSQLHOST}}` | ✅ |
| `MYSQL_PORT` | `${{MySQL.MYSQLPORT}}` | ✅ |
| `MYSQL_DATABASE` | `${{MySQL.MYSQLDATABASE}}` | ✅ |
| `MYSQL_USER` | `${{MySQL.MYSQLUSER}}` | ✅ |
| `MYSQL_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` | ✅ |
| `REDIS_HOST` | `${{Redis.REDISHOST}}` | ✅ |
| `REDIS_PORT` | `${{Redis.REDISPORT}}` | ✅ |
| `JWT_ACCESS_SECRET` | 랜덤 32자 이상 (재사용 금지) | ✅ |
| `JWT_REFRESH_SECRET` | 랜덤 32자 이상 (access와 다른 값) | ✅ |
| `KAKAO_CLIENT_ID` | 카카오 디벨로퍼스 REST API 키 | ✅ |
| `KAKAO_CLIENT_SECRET` | 카카오 client_secret | ✅ |
| `KAKAO_REDIRECT_URI` | `https://<vercel도메인>/api/v1/auth/kakao/callback` (④에서 확정) | ✅ |
| `CORS_ALLOWED_ORIGINS` | `https://<vercel도메인>` (④에서 확정) | ✅ |
| `COOKIE_SECURE` | `true` (prod 기본값이라 생략 가능) | – |
| `COOKIE_SAME_SITE` | `Lax` (프록시 방식이라 Lax로 충분, 생략 가능) | – |
| `COOKIE_DOMAIN` | (비움 — host-only 쿠키) | – |
| `OPENAI_API_KEY` | AI 분류 켤 때만. **지금은 비움** (아래 "AI 분류" 참고) | – |
| `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` / `S3_BUCKET_NAME` | 이미지 첨부 기능용. 지금은 비워도 됨 | – |

> ⚠️ **Railway MySQL SSL 주의**: `application-prod.yaml`의 JDBC URL은 `useSSL=true`다. Railway 내부 네트워크 연결이 SSL을 안 쓰면 연결 실패할 수 있다 → 배포 시 로그 확인 후, 필요하면 prod yaml의 `useSSL`을 `false`로 조정(또는 env로 빼기).

> 🔑 시크릿 생성 예: `openssl rand -base64 48`

---

## 2. 백엔드 URL 확보

Railway 배포 성공 후 `https://<something>.up.railway.app/actuator/health` 가 `{"status":"UP"}` 뜨는지 확인.

---

## 3. 프론트 — Vercel

1. `frontend/vercel.json`의 `REPLACE_WITH_RAILWAY_BACKEND_URL`을 **2에서 확보한 Railway 도메인**으로 교체 (`https://` 없이 호스트만, 예: `curio-backend-production.up.railway.app`)
2. [vercel.com](https://vercel.com) 가입 → **Add New → Project** → `wooqqq/curio` import
   - **Root Directory**: `frontend`
   - Framework Preset: Vite (자동 감지)
3. 배포 → 생성된 도메인 확인 (예: `curio.vercel.app`)

> 프론트 코드(`client.js`의 `baseURL: '/api/v1'`)는 **수정 불필요**. Vercel 프록시가 same-origin으로 백엔드에 전달한다.

---

## 4. 후속 연결 (도메인 확정 후 되돌아와 채우기)

Vercel 도메인이 정해졌으니 ①의 미확정 값들을 채운다:

- 백엔드 `KAKAO_REDIRECT_URI` = `https://<vercel도메인>/api/v1/auth/kakao/callback`
- 백엔드 `CORS_ALLOWED_ORIGINS` = `https://<vercel도메인>`
- **카카오 디벨로퍼스 콘솔** → 카카오 로그인 → Redirect URI에 위 콜백 URL 등록
- 변경 후 Railway 백엔드 재배포(env 바뀌면 자동 재배포됨)

---

## 5. 배포 트리거 — develop → main

CI(`.github/workflows/ci.yml`)가 그린이면 `develop`을 `main`으로 머지 → Railway/Vercel이 `main` 푸시를 감지해 자동 배포.

```
develop 작업 → PR(main) → CI 통과 → 머지 → 자동 배포
```

> Railway/Vercel의 배포 대상 브랜치를 `main`으로 설정해 둘 것.

---

## 부록: AI 카테고리 분류

현재 `OPENAI_API_KEY`가 비어 있으면 분류가 보류되어 모든 아이템 `category=null`(= "전체"에만 노출)이다. 배포 시 선택:

- **(권장) 지금은 키 비움** — 비용 0으로 먼저 띄움. 나중에 키 넣고 `POST /api/v1/items/reclassify-all` 1회로 기존 아이템 일괄 분류.
- **지금 켜기** — `OPENAI_API_KEY` 입력(OpenAI $5 충전) 또는 Gemini 무료티어로 `OpenAiService` 교체 후 사용.

> 카테고리 탭 UX는 분류가 채워지기 전까진 "전체"만 의미 있으므로, 키를 안 켤 거면 프론트 카테고리 필터를 임시로 숨기는 것도 방법.
