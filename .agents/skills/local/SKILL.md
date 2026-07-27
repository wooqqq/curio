---
name: local
description: Curio 로컬 개발 환경을 켜고/끄고/상태확인한다. 인프라(Docker MySQL+Redis)와 프론트(Vite)만 담당하고 백엔드는 사용자가 IDE에서 직접 띄운다. "로컬 켜줘/올려줘", "로컬 내려줘/꺼줘", "로컬 정리", "local up/down/status" 류 요청에 해당.
---

# 로컬 환경 on/off (local)

Curio 로컬 개발 환경을 띄우고 내린다. **백엔드는 이 스킬이 건드리지 않는다** — 사용자가 IntelliJ에서 직접 실행한다. 이 스킬은 **Docker(MySQL+Redis)와 Vite 프론트**만 책임진다.

인자로 동작을 고른다: `up`(기본) / `down` / `status`. 인자가 없으면 `up`으로 본다.

## 전제 (왜 이렇게 나눴나)
- 백엔드는 `develop` 작업 시 사용자가 **IDE에서 디버깅하며** 돌린다. 그래서 8080은 IDE 차지 — 이 스킬이 백엔드를 띄우면 포트가 충돌난다.
- **백엔드는 dotenv 의존성이 없다.** IDE에서 띄울 때 루트 `.env`가 환경변수로 주입돼야 OPENAI·S3·JWT·DB 크레덴셜이 들어간다(EnvFile 플러그인 또는 run config). 안 하면 "부팅은 되는데 AI 분류·S3 업로드만 조용히 빠지는" 헷갈리는 상태가 된다. 이 스킬은 이걸 **실행하지 않고 안내만** 한다(루트 `.env`는 백엔드용이고 Vite는 자기 환경변수를 쓴다).

## up — 환경 올리기
1. **Docker 기동**: `docker compose up -d`. 데몬 연결 실패(Docker Desktop 꺼짐)면 사용자에게 Docker Desktop을 켜라고 안내하고 멈춘다.
2. **헬스 대기**: MySQL·Redis가 `healthy`가 될 때까지 기다린다. **foreground `sleep`은 막혀 있으니** 백그라운드 until-루프로 폴링한다. 예:
   ```
   until [ "$(docker inspect -f '{{.State.Health.Status}}' curio-mysql)" = healthy ] && \
         [ "$(docker inspect -f '{{.State.Health.Status}}' curio-redis)" = healthy ]; do sleep 2; done
   ```
   (run_in_background 으로 띄우고 완료 알림을 기다린다.)
3. **Vite 기동**: `npm --prefix frontend run dev` 를 **백그라운드**로. 처음이면 `node_modules` 없을 때만 `npm --prefix frontend install` 먼저.
4. **보고**: 출력에서 Vite URL(보통 `http://localhost:5173`)을 집어 알려준다. 그리고 **사용자에게 IDE 백엔드를 띄우라고 안내** — 그때 `.env` 환경변수 주입이 걸려 있는지 확인하라는 한 줄을 꼭 붙인다.

## down — 환경 내리기
1. **Vite 종료**: 5173 포트를 LISTEN 중인 프로세스를 찾아 `Stop-Process -Force`. 없으면 그렇게 보고.
2. **Docker 정리**: `docker compose down`. 데몬이 이미 꺼져 있으면(Docker Desktop 종료) 컨테이너도 이미 멈춘 상태라 무해 — 그렇게 설명하고 넘어간다. 데이터는 named volume(`curio-mysql-data` 등)에 유지되니 `down`은 안전하다(볼륨까지 지우는 `-v`는 쓰지 않는다).
3. **백엔드는 건드리지 않는다** — IDE 인스턴스는 사용자가 직접 멈춘다고 안내.

## status — 상태 확인
인자가 `status`면 **아무것도 켜거나 끄지 않고** 현재 무엇이 떠 있는지만 한눈에 보고한다.

1. **Docker 데몬**: `docker ps`가 데몬 연결 실패면 Docker Desktop 꺼짐 → MySQL·Redis는 전부 "내려감"으로 처리하고 컨테이너 점검은 건너뛴다.
2. **컨테이너**: `curio-mysql`·`curio-redis` 각각
   `docker inspect -f '{{.State.Status}} / {{.State.Health.Status}}' curio-mysql` 로 실행+health 확인. 컨테이너가 없으면(`No such object`) "내려감".
3. **포트**: PowerShell `Get-NetTCPConnection -LocalPort N -State Listen` 으로 5173(Vite)·8080(백엔드) LISTEN 여부.
4. **보고**: 아래처럼 컴포넌트별 상태표로 정리한다(이모지로 한눈에).
   - MySQL (curio-mysql) — 🟢 healthy / 🟡 실행 중(health 대기) / 🔴 내려감
   - Redis (curio-redis) — 🟢 healthy / 🟡 실행 중 / 🔴 내려감
   - Vite (5173) — 🟢 실행 중 / ⚪ 안 띄움
   - 백엔드 (8080) — 🟢 IDE 실행 중 / ⚪ 미실행 (이 스킬이 띄우지 않음)

   8080이 떠 있으면 "IDE 백엔드 실행 중", 안 떠 있으면 "IDE에서 직접 띄워야 함"으로 해석해 한 줄 덧붙인다.

## 주의
- 포트 점유 프로세스 조회/종료는 PowerShell(`Get-NetTCPConnection -LocalPort N -State Listen`)을 쓴다.
- 헬스/완료 대기에 foreground `sleep` 체이닝 금지 — 백그라운드 until-루프로 한다.
- `.env` 값을 대화창에 출력하지 않는다(키 노출 방지). 존재 여부·키 이름까지만.
