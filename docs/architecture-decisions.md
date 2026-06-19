# 설계 결정 기록

코드만 봐서는 알기 어려운 "왜 이렇게 했는지"를 모아둔 문서. 무엇을 만들었는지는 `CLAUDE.md`, 진행 순서는 `PROGRESS.md`를 본다.

각 항목은 *상황 → 선택 → 이유 → 아쉬운 점* 순으로 적었다.

---

## 1. 카카오 로그인을 Spring Security OAuth2 Client로 안 하고 RestTemplate으로 직접 호출

처음엔 당연히 Spring Security의 OAuth2 Client를 쓰려고 했다. 그런데 카카오 앱에서 client_secret을 켜둔 상태에서 OAuth2 Client가 토큰 요청에 PKCE를 자동으로 붙였고, 카카오가 이 조합을 거절했다. 클라이언트 인증 방식을 `none`으로 두면 PKCE가 자동 활성화되는 구조라 우회가 까다로웠다.

결국 토큰 요청 파라미터를 완전히 통제하려고 `RestTemplate`으로 카카오 토큰/유저 API를 직접 호출하는 방식으로 바꿨다 (`KakaoAuthService`). Spring Security는 OAuth 흐름에서 빼고, **JWT 필터 + 인가 규칙** 용도로만 남겼다 (`SecurityConfig`는 `STATELESS`, `JwtFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록).

- 장점: 카카오 쪽 요청을 우리가 직접 다루니 디버깅이 명확하다.
- 아쉬운 점: OAuth2 Client가 자동으로 해주던 부분(state 검증 등)을 직접 챙겨야 한다. 다른 소셜 로그인을 붙이면 보일러플레이트가 늘어난다.

## 2. JWT Access/Refresh + Refresh를 DB에 저장

Access는 짧게, Refresh로 재발급하는 표준 구조. 다만 Refresh 토큰을 stateless하게 두지 않고 `refresh_tokens` 테이블에 저장한다.

이유는 서버가 "현재 유효한 Refresh"를 알아야 하기 때문이다. 재발급 시 토큰이 실제로 살아있는지 확인하고(`AuthService.reissue`), 로그아웃 시 서버에서 토큰을 무효화한다(`logout`에서 유저의 Refresh 삭제). 순수 stateless JWT로는 로그아웃·강제 만료가 안 된다.

- 아쉬운 점: 재발급마다 DB 조회가 든다. 트래픽이 커지면 Redis로 옮기는 게 맞다.
- 후속: 처음엔 재발급 때 Refresh를 갈아끼우는 **rotation**도 했지만, 콜백 직후·새로고침마다 재발급을 호출하는 구조와 맞물려 race로 멀쩡한 세션이 로그아웃되는 문제가 생겨 rotation은 걷어냈다. 자세한 건 #11.
- 해소된 빚: 콜백에서 토큰을 URL 쿼리로 넘기던 방식은 #11에서 httpOnly 쿠키로 교체했다.

## 3. 카카오 챗봇 5초 제한 → 즉시 응답 후 비동기 처리

카카오 오픈빌더 스킬 서버는 5초 안에 응답해야 한다. 그런데 링크 한 건을 저장하려면 OG 크롤링 + AI 분류 + (이미지면) S3 업로드까지 돌아서 5초를 넘기기 쉽다.

그래서 `KakaoController`는 받자마자 큐에 넣고 "저장 중이에요" 텍스트를 즉시 돌려준다. 실제 처리는 뒤에서 따로 진행된다.

```
KakaoController → QueueService.enqueue() → ItemProcessor.process()
```

사용자 입장에선 바로 응답이 오고, 무거운 작업은 백그라운드에서 끝난다.

- 아쉬운 점: 처리가 실패해도 사용자는 "저장 중"만 보고 끝난다. 실패 알림이나 상태 추적이 아직 없다.

## 4. QueueService를 인터페이스로 분리

지금 비동기 처리는 그냥 Spring `@Async`다 (`AsyncQueueService`, 전용 스레드풀 `itemProcessorExecutor` — core 5 / max 20 / queue 100, `AsyncConfig`). 인메모리 큐라 서버가 죽으면 처리 중이던 작업은 날아간다.

MVP엔 이걸로 충분하다고 봤지만, 나중에 유실 없는 처리나 재시도가 필요해지면 Redis Stream 같은 걸로 갈아끼울 걸 예상했다. 그래서 호출부(`KakaoController`)는 `QueueService` 인터페이스에만 의존하게 했다. 구현체를 바꿔도 컨트롤러는 안 건드린다.

- 아쉬운 점: 인터페이스 메서드가 `enqueue(userId, utterance)` 하나뿐이라 지금은 추상화 이득이 작다. "나중을 위한" 설계라 YAGNI로 볼 여지도 있다. 다만 교체 비용이 큰 지점이라 미리 끊어둔 쪽을 택했다.

## 5. 중복 링크 방지: user + normalizedUrl UNIQUE

같은 글을 두 번 보내도 한 번만 저장되게 하려고 `items`에 `(user_id, normalized_url)` 유니크 제약을 뒀다. 저장 전에 `existsByUserAndNormalizedUrl`로도 한 번 거른다.

URL을 그대로 비교하면 쿼리스트링·끝 슬래시·대소문자 때문에 같은 글이 다르게 취급된다. 그래서 `ItemProcessor.normalizeUrl`에서 scheme+host(소문자)+path(끝 슬래시 제거)만 남겨 정규화한 값을 비교 키로 쓴다.

- 아쉬운 점: 정규화가 단순해서 쿼리스트링으로 글이 구분되는 사이트(예: `?id=`)는 중복으로 못 잡거나 반대로 다른 글을 같다고 볼 수 있다. 트래킹 파라미터(utm 등) 제거도 아직 안 한다.

## 6. 응답 포맷 통일 — 단, 카카오 스킬은 예외

일반 API는 `{ code, data, message }` 형태(`ApiResponse`)로 통일하고, 에러는 `ErrorCode` enum + `GlobalExceptionHandler`에서 일괄 변환한다. 프론트가 응답 구조를 하나로 가정할 수 있다.

예외는 카카오 스킬 엔드포인트다(`POST /api/v1/kakao/skill`). 여기는 우리 포맷이 아니라 오픈빌더가 요구하는 응답 스키마(`KakaoSkillResponse`)를 그대로 돌려줘야 해서 `ApiResponse`로 감싸지 않는다. 외부 규격을 우리 규격에 억지로 맞추지 않았다.

## 7. 검색은 별도 엔드포인트 대신 GET /items에 q 통합

검색을 `/items/search`로 따로 빼지 않고 기존 목록 API에 `q` 파라미터를 추가했다(`GET /api/v1/items?category=&q=`). 카테고리 필터와 검색이 자연스럽게 같이 걸리기 때문이다.

레포지토리도 조건별로 메서드를 나누지 않고 JPQL 한 방으로 처리한다(`ItemRepository.search`). `(:param IS NULL OR ...)` 패턴으로 category·q가 있을 때만 조건이 붙는다. 메서드를 분리하면 (전체 / 카테고리만 / 검색만 / 둘 다) 조합이 4개로 늘어나서다. 태그까지 검색하려고 `LEFT JOIN i.tags` + `DISTINCT`를 썼고, 페이지네이션 때문에 count 쿼리는 따로 지정했다.

- 아쉬운 점: LIKE `%키워드%` 기반이라 인덱스를 못 타고, 형태소 분석도 없다. 데이터가 커지면 풀텍스트 인덱스나 검색 엔진이 필요하다. 개인 아카이브 규모에선 아직 문제없다고 판단.

## 8. OG 크롤링은 실패를 전제로 설계

링크를 저장할 때 제목/요약/썸네일을 OG 태그에서 긁어온다(`OgCrawlerService`). 외부 사이트라 언제든 실패할 수 있어서, 처음엔 실패하면 제목 자리에 raw URL을 그대로 넣었다. 그 결과 한글이 퍼센트 인코딩된 URL이 제목으로 박히는 사고가 났다.

지금은 세 겹으로 막는다.
- 봇 UA 대신 실제 브라우저 UA + `Accept-Language: ko`, 그리고 1회 재시도. (일시적 실패가 주 원인이었다.)
- 그래도 실패하면 raw URL 대신 URL 슬러그를 디코딩해 제목을 복원한다(`titleFromUrl`). 끝의 해시 id 제거, 하이픈을 공백으로. 슬러그가 의미 없으면 호스트명으로.
- 이미 깨진 채 저장된 건 `POST /items/{id}/recrawl`로 다시 긁어 복구한다. 프론트는 제목이 URL로 깨진 카드에만 "다시 불러오기" 버튼을 띄운다.

핵심은 폴백이 "그럴듯한 기본값"이어야지 raw 데이터를 그대로 노출하면 안 된다는 것. (별도 TIL로도 정리)

## 9. 입력 타입은 규칙 기반으로 감지

들어온 텍스트가 LINK / IMAGE / TEXT 중 뭔지 `ItemProcessor.detectType`에서 정규식으로 판단한다. URL 패턴이 있으면 링크, 그 URL이 이미지 확장자(jpg/png/...)로 끝나면 이미지, 아니면 텍스트.

AI에 맡길 수도 있었지만 타입 판별 같은 단순 분기에 LLM 호출을 쓰면 비용·지연만 늘고 결과도 덜 예측 가능하다. AI는 그 다음 단계인 카테고리/태그 분류에만 쓴다.

## 10. ddl-auto: update

스키마를 Hibernate `update`로 자동 반영한다. 초반에 엔티티가 자주 바뀌는 동안 마이그레이션 파일을 매번 쓰는 부담을 덜려고 택했다.

- 아쉬운 점: 운영에선 위험하다(컬럼 삭제·타입 변경을 update가 안전하게 처리 못 함). 배포 단계에서 Flyway 같은 마이그레이션 도구로 전환할 예정.

## 11. OAuth 토큰 전달을 httpOnly 쿠키 + 메모리 access로

처음엔 로그인 콜백에서 access·refresh를 둘 다 URL 쿼리로 프론트에 넘기고(`/auth/callback?accessToken=...&refreshToken=...`), 프론트는 둘 다 `localStorage`에 저장했다. 동작은 했지만 노출 표면이 두 겹이었다. 토큰이 URL에 실리면 브라우저 히스토리·서버 액세스 로그·Referer 헤더에 남고, `localStorage`는 JS가 다 읽을 수 있어 XSS 한 번이면 장수명 refresh까지 털린다.

배포 전 마지막 보안 작업으로 다음과 같이 바꿨다.

- **refresh = httpOnly 쿠키.** 콜백에서 `Set-Cookie: HttpOnly; Secure; SameSite; Path=/api/v1/auth`로 내려주고 URL엔 토큰을 싣지 않는다(`CookieUtil`, `AuthController.kakaoCallback`). JS(`document.cookie`)로 못 읽으니 XSS로 탈취 불가. Path를 auth 경로로 좁혀 일반 API 요청엔 안 실리게 했다(CSRF 표면 축소).
- **access = 메모리(zustand).** `localStorage`를 아예 안 쓴다. 새로고침하면 메모리가 비므로, 앱 부팅 때 refresh 쿠키로 `/reissue`를 1회 호출해 access를 복구한다(`App.jsx`). 복구가 끝나기 전엔 라우팅 판단을 보류한다(`bootstrapped` 플래그).
- **쿠키 속성은 프로필별로.** dev는 http라 `Secure=false`, prod는 `Secure=true`. 같은 사이트(`mycurio.kr`/`api.mycurio.kr`)면 `SameSite=Lax`로 충분하고, 완전히 다른 도메인 배포면 `SameSite=None`이 필요해 배포 토폴로지가 정해질 때 확정한다.

대안으로 "일회성 code 교환"(토큰을 Redis에 짧은 code로 저장하고 URL엔 code만)도 검토했지만, 그건 URL 노출만 가리고 `localStorage`-XSS 노출은 그대로 남는 절반짜리라 택하지 않았다.

이 과정에서 두 가지를 더 손봤다.
- **rotation 제거.** 콜백 직후·새로고침마다 `/reissue`가 도는 새 구조에서 rotation(기존 refresh 삭제 후 재발급)을 유지하니, 빠른 새로고침·멀티탭에서 한 요청이 막 회전시켜 지운 토큰을 다른 요청이 들고 와 `REFRESH_TOKEN_NOT_FOUND`로 로그아웃되는 race가 났다. reissue는 access만 새로 발급하고 refresh는 만료까지 유지하도록 바꿔 race를 없앴다. 제대로 된 rotation은 토큰 패밀리·재사용 감지까지 가야 하는 영역이라, MVP에선 득보다 실이 컸다.
- **JWT에 jti(UUID) 추가.** Refresh JWT가 `{sub, iat(초), exp}`뿐이라 같은 유저를 같은 초에 발급하면 토큰 문자열이 byte 단위로 동일했다. 콜백이 막 만든 토큰과 직후 reissue가 만든 토큰이 겹쳐 `token` UNIQUE 제약에 걸렸다(Hibernate가 한 트랜잭션에서 INSERT를 DELETE보다 먼저 실행해 터졌다). 모든 토큰에 고유 `jti`를 넣어 충돌을 원천 차단했다. (별도 TIL로도 정리)

- 아쉬운 점: access를 메모리에만 두니 새로고침마다 `/reissue` 왕복이 한 번 더 든다(부팅 직전 짧은 로딩). 보안과 맞바꾼 비용이라 수용했다. rotation을 뺐으니 refresh 토큰 탈취 시 자동 감지(재사용 탐지)는 없다 — httpOnly로 탈취 가능성 자체를 낮추는 쪽에 무게를 뒀다.

## 12. 배포는 Railway(백엔드) + Vercel(프론트), EC2 대신

원래는 AWS EC2 프리티어를 막연히 생각했다. 그런데 AWS가 2025년 7월부터 신규 계정 프리티어를 "12개월 always-free"에서 "6개월 크레딧"으로 바꿔서, 2026년에 만든 이 계정은 EC2를 올려도 사실상 6개월 뒤 과금이다. 게다가 EC2는 t2.micro 1GB에 Spring Boot + MySQL + Redis를 올리면 메모리가 빠듯해 swap·nginx·certbot까지 직접 챙겨야 한다.

개인 프로젝트의 목표가 "동작하는 서비스를 빨리 띄워 포트폴리오로 쓰는 것"이라, 인프라 관리가 거의 없는 **Railway**(Docker 자동 빌드, MySQL·Redis 애드온)로 백엔드를, **Vercel**로 프론트를 올렸다. 코드는 환경변수로 전부 분리돼 있어, 나중에 인프라 학습용으로 EC2 이전을 따로 하더라도 설정만 옮기면 된다(코드 변경 0).

배포하면서 로컬 Docker에선 멀쩡하던 게 Railway에서 줄줄이 터졌는데, 전부 "로컬 ↔ 매니지드 환경 차이"였다. MySQL은 Railway 내부 네트워크가 SSL을 안 써서 `useSSL=false&allowPublicKeyRetrieval=true`로 맞춰야 했고, Redis는 로컬과 달리 비번 인증을 요구했고(NOAUTH), DB 접속 정보는 로컬 `.env` 값이 아니라 `${{MySQL.*}}` 참조여야 했다. 그 과정에서 prod 설정 원칙도 하나 세웠다 — **선택 통합(AWS·OpenAI)은 `${VAR:}` 빈 기본값을 줘 미설정이어도 부팅되게(기능만 꺼짐), 필수 설정(CORS·카카오 리다이렉트)은 기본값 없이 빨리 실패하게** 둔다. dev는 모든 값에 기본값이 있어 안 드러나던 문제다.

- 아쉬운 점: Railway·Vercel 무료 한도를 넘기면 과금되고, 백엔드+MySQL+Redis 3개라 크레딧을 빠르게 쓴다. 매니지드라 인프라를 직접 다루는 학습은 EC2 이전 때로 미뤘다.

## 13. 프론트–백엔드를 Vercel 프록시로 묶어 same-origin 쿠키 유지

#11에서 refresh를 httpOnly 쿠키로 옮기면서, "프론트(vercel.app)와 백엔드(railway.app)가 완전히 다른 도메인이면 cross-site라 `SameSite=None; Secure`가 필요하고 CORS도 credentials 모드로 풀어야 한다"는 숙제가 남아 있었다. SameSite=None은 서드파티 쿠키 취급이라 브라우저 정책에 더 취약하다.

이걸 도메인을 사는 대신 **Vercel 리라이트 프록시**로 풀었다. `frontend/vercel.json`에서 `/api/*`를 Railway 백엔드로 프록시하면, 브라우저 입장에선 모든 요청이 vercel.app 한 출처로 나간다. 그래서 refresh 쿠키가 **first-party(SameSite=Lax)**로 그대로 동작하고, 카카오 콜백도 vercel.app을 거쳐 쿠키가 first-party로 깔린다. 결과적으로 SameSite=None도, CORS credentials도, 프론트 API 클라이언트의 baseURL 변경(`/api/v1` 상대경로 유지)도 필요 없었다.

- 이유: 같은 출처로 만들면 쿠키 보안 모델이 가장 단순하고 깨질 구석이 적다. 도메인 구매(mycurio.kr)를 배포 필수 선결조건에서 떼어낼 수 있던 것도 컸다.
- 아쉬운 점: 모든 API가 Vercel을 한 번 더 경유해 약간의 지연이 붙는다. 그리고 `vercel.json`의 프록시 대상에 Railway 주소가 박혀 있어, 백엔드 주소가 바뀌면 이 파일도 같이 고쳐야 한다.

## 14. 아이템 목록 N+1을 fetch join 대신 배치 페치로

아카이브 진입이 느린 게 쿼리 탓인지 의심해 목록 조회 경로를 뜯어봤다. `ItemResponse.from`이 아이템마다 lazy `@ManyToMany`인 `getTags()`를 건드려서, 한 페이지(20개)를 그리면 아이템 1번 + 태그 20번 = 21쿼리가 나가는 N+1이 있었다.

흔한 해법인 `LEFT JOIN FETCH`는 여기선 못 썼다. 목록은 페이지네이션을 쓰는데, 컬렉션을 fetch join한 채 `setMaxResults`를 걸면 Hibernate가 DB 페이징을 못 하고 전부 메모리로 읽어 자른다(HHH000104). 그래서 fetch join 대신 `hibernate.default_batch_fetch_size=100`을 켰다. 태그를 아이템마다 따로 읽던 걸 `tag_id IN (...)` 한두 방으로 묶어 N+1을 없애면서, 페이지네이션은 그대로 DB에서 처리된다.

다만 진단해보니 체감 로딩의 대부분은 쿼리가 아니라 **Railway 콜드 스타트**(유휴 시 인스턴스 슬립 + JVM 부팅)였다. N+1 정리는 데이터가 쌓일 때를 대비한 성격이 크고, 콜드 스타트는 헬스체크 핑(UptimeRobot)으로 깨워두면 잡히지만 그만큼 Railway 크레딧을 상시 태운다. 개인 프로젝트 규모에선 첫 진입 몇 초를 감수하는 게 낫다고 보고 핑은 보류했다(필요하면 데모 직전에만 켜는 식).

- 아쉬운 점: 부팅 직후 `reissue → items` 두 왕복은 items가 access 토큰에 의존해 직렬일 수밖에 없어, 콜드일 때 지연이 누적된다. 근본 해소는 상시 가동(유료)뿐이라 미뤘다.

## 15. 관리자 권한을 role 컬럼 대신 ADMIN_KAKAO_IDS allowlist로

공지/팝업 관리자 페이지를 붙이면서 "관리자"를 어떻게 식별할지 정해야 했다. 정석은 `users`에 `role` 컬럼을 두거나 JWT에 권한 클레임을 싣고 `@PreAuthorize`로 거는 것이다. 하지만 이 서비스의 관리자는 사실상 운영자 본인 한 명이다.

그래서 스키마·토큰을 건드리지 않고, `ADMIN_KAKAO_IDS` 환경변수(쉼표 구분 `kakao_id` 목록)에 든 유저만 관리자로 보는 allowlist 방식을 택했다(`AdminGuard`). 관리자 전용 엔드포인트(`/api/v1/admin/**`) 진입부에서 현재 `userId`로 유저를 조회해 그 `kakao_id`가 목록에 있는지만 확인한다. 프론트는 `GET /admin/check`로 관리자 여부를 받아 "관리자" 링크 노출·`/admin` 접근을 가른다(백엔드도 독립적으로 막으니 프론트 가드는 UX용).

- 이유: role 시스템은 다중 관리자·권한 등급이 생길 때 의미가 있는데 지금은 둘 다 없다. allowlist는 스키마 변경 0, 되돌리기 쉽고, 관리자 추가도 환경변수만 고치면 된다. 값은 코드/대화에 남기지 않고 Railway·로컬 `.env`에만 둔다.
- 아쉬운 점: 권한이 코드가 아니라 환경변수에 있어, 누가 관리자인지는 배포 환경을 봐야 안다. 진짜 다중 관리자·세분화된 권한이 필요해지면 그때 role로 승격해야 한다.

## 16. 팝업–공지를 FK 대신 linkUrl로 잇고, 활성 단일 보장은 앱 로직으로

공지(`Announcement`)와 진입 팝업(`Popup`)을 별도 엔티티로 두되, 둘을 외래키로 묶지 않고 팝업의 `linkUrl`(자유 문자열)로 느슨하게 연결했다. 관리자가 공지 #3을 쓰고 팝업 `linkUrl`에 `/announcements/3`을 넣으면 팝업 클릭 시 그 공지로 이동한다. 외부 URL(`https://...`)도 그대로 넣을 수 있다. FK로 강결합하면 "외부 링크 팝업"이나 "링크 없는 공지 팝업" 같은 경우가 어색해진다 — 느슨하게 둬서 유연성을 얻었다.

활성 팝업은 "항상 최대 1개"가 규칙인데, 이걸 DB 제약으로 강제하지 않고 앱 로직으로 처리한다(새 팝업을 활성화할 때 기존 활성 건을 끈다 `PopupService`, 조회는 `findFirstByActiveTrueOrderByCreatedAtDesc`로 최신 1개만). 코드리뷰는 "동시 요청에 깨질 수 있으니 active=true에 unique 제약을 걸라"고 제안했지만 택하지 않았다. 이유는 두 가지다. ① 관리자는 1명 + 단일 인스턴스라 동시 활성화가 현실적으로 일어나지 않는다. ② unique 제약은 정상 경로를 오히려 깬다 — 새 팝업 INSERT와 기존 팝업 UPDATE(active=false)가 한 트랜잭션에 묶이는데, Hibernate는 flush 시 INSERT를 UPDATE보다 먼저 실행해 그 순간 active 행이 2개가 되어 제약에 걸린다(#11에서 refresh 토큰으로 이미 겪은 insert-before-delete와 같은 함정). 게다가 조회가 최신 1개만 집으므로 설령 active가 2개 남아도 사용자에겐 하나만 보인다 — 표시 단에서 이미 단일 보장이 된다.

- 이유: 1인 규모에서 이론상 0에 가까운 동시성 위험을 막으려고 흔한 경로를 깨뜨리는 건 손해다. 코드리뷰의 "최악의 분산 환경" 가정을 그대로 따르면 오버엔지니어링이 된다.
- 아쉬운 점: 진짜 다중 관리자 환경이 되면 활성 단일 보장이 앱 로직만으로는 경합에 취약하다. 그땐 비관적 락이나 단일 활성 포인터 테이블 같은 다른 방식이 필요하다.

## 17. 웹에서 링크 추가는 봇과 달리 동기 처리

봇 경로는 #3 때문에 비동기다 — 카카오 5초 제한이 있어 큐에 넣고 "저장 중"만 돌려준다. 그런데 웹에서 FAB로 링크를 직접 추가하는 경로(`POST /items`)는 같은 제약이 없다. 사용자가 화면 앞에서 결과를 기다리고 있으므로, 크롤·분류·저장을 그 자리에서 끝내고 **저장된 아이템을 응답으로 돌려준다**(`ItemProcessor.addLink`, 동기). 프론트는 그 아이템을 받아 피드 맨 앞에 즉시 꽂는다.

링크를 실제로 만드는 로직(`saveLink`)은 봇 경로(`process`)와 공유하고, 진입점만 동기/비동기로 갈렸다. 중복 URL이면 봇은 조용히 무시했지만 웹은 사용자가 결과를 봐야 하므로 `DUPLICATE_URL` 에러로 명시적으로 알린다.

- 이유: 입력 채널의 제약(봇=5초)과 UX 기대(웹=결과 즉시 확인)가 달라 같은 저장 로직에 진입 방식만 다르게 뒀다.
- 아쉬운 점: 동기라 크롤이 느린 사이트면 사용자가 그만큼 기다린다(유튜브 타임아웃이 이래서 체감됐고 #18로 완화). 정 느린 곳이 늘면 웹도 낙관적 추가 후 백그라운드 보강으로 갈 수 있다.

## 18. 유튜브 링크는 일반 스크래핑 대신 oEmbed로

웹 링크 추가를 테스트하다 유튜브(`m.youtube.com`) 링크가 제목을 못 가져오고 10초씩 걸리는 걸 발견했다. 유튜브는 JS 렌더링 페이지라 Jsoup이 본문을 끝까지 못 읽고 타임아웃(5초×2회)났고, 그 뒤 `titleFromUrl` 폴백이 경로 `/watch`에서 "watch"를 제목으로 집었다.

그래서 호스트가 유튜브 계열이면 공식 oEmbed 엔드포인트(`youtube.com/oembed?format=json&url=...`)를 먼저 호출해 제목·썸네일·채널명을 받는다(`OgCrawlerService.crawlYouTube`). oEmbed가 비정상(비공개/삭제 영상 등)이면 `null`을 돌려 기존 OG 크롤로 폴백한다.

- 이유: oEmbed는 구글이 공개·문서화한 메타데이터 API라 스크래핑보다 빠르고 안정적이고, 컴플라이언스상으로도 비공인 긁기보다 떳떳하다(키 불필요, Data API 쿼터와 무관).
- 아쉬운 점: 유튜브 호스트 하드코딩이라 다른 JS 렌더링 사이트는 여전히 느릴 수 있다. 대량 호출 시 oEmbed 레이트리밋 가능성이 있어 트래픽이 커지면 결과 캐싱이 필요하다.

## 19. AI 분류를 OpenAI에서 Gemini 무료티어로 전환

OpenAI(gpt-4.1-mini)로 분류하다가 계정 크레딧이 소진돼 `429 insufficient_quota`로 분류가 매번 실패했다. 개인 포트폴리오 단계에서 매달 결제를 유지하기 부담스러워, 카드 없이 구글 계정만으로 키를 발급받는 Google AI Studio(Gemini) 무료티어로 갈아탔다.

분류 작업 자체(카테고리 4→3 + 태그)는 가벼워서 무거운 모델이 필요 없다. `OpenAiService`를 `GeminiService`로 교체해 HTTP 호출만 Gemini `generateContent`로 바꿨고(키는 `x-goog-api-key` 헤더, 응답은 `responseMimeType: application/json`으로 순수 JSON 강제), 프롬프트·`ClassificationResult`·`ItemClassifier`·파이프라인은 그대로 뒀다. 호출부가 `GeminiService` 하나만 알면 되도록 좁혀, 나중에 또 공급자를 바꿔도 이 서비스만 갈면 된다.

모델은 처음에 `gemini-2.0-flash`로 잡았는데 그게 무료티어에서 429를 뱉었다. 직접 키로 때려보니 같은 무료티어라도 **모델마다 쿼터가 따로**여서 `gemini-2.0-flash`는 소진/제한, `gemini-2.5-flash`는 정상이었다. 그래서 기본 모델을 `gemini-2.5-flash`로 확정했다(`GEMINI_MODEL`로 오버라이드 가능).

- 이유: 비용 0 + 카드 불필요라 개인 단계에 맞고, 교체 비용이 서비스 1개로 작았다.
- 아쉬운 점: 무료티어는 보낸 내용이 모델 학습에 쓰일 수 있다(유료/OpenAI는 안 씀) — 사용자 저장 내용을 보내므로 프라이버시 트레이드오프가 있다. 신경 쓰이면 유료나 로컬(Ollama)로 옮기면 되고, 그때도 `GeminiService`만 바꾸면 된다. 무료티어 분당 제한(RPM)에 걸리면 그 호출은 실패하는데, 이건 #21로 완화했다.

## 20. 카테고리에서 JOB을 빼고 CAREER(커리어/취업)로 통합

원래 카테고리가 DEVELOPMENT / CAREER / JOB / ETC 넷이었는데, 분류 결과를 보다 보니 CAREER와 JOB의 경계가 계속 흔들렸다. 예로 "자소서 검색기"는 취업 준비물(CAREER)이자 채용 절차(JOB)라 어느 쪽으로 규칙을 박아도 반대 케이스가 깨진다. 이건 모델 문제가 아니라 **타겟(취준생)에겐 그 둘이 한 덩어리인데 너무 잘게 쪼갠 설계** 문제였다.

그래서 JOB을 CAREER로 흡수해 DEVELOPMENT / CAREER / ETC 셋으로 줄였다. 카테고리는 큼직한 내비게이션 버킷으로 두고, `자소서`·`면접`·`채용`·`연봉` 같은 세부는 이미 **태그**가 담당한다 — 두 career 버킷은 태그가 할 일을 카테고리가 중복하는 셈이었다. `Category` enum에서 JOB 제거, 프롬프트 3개로, 프론트 탭 '취업' 제거·'커리어/취업'로 통합했다.

- 데이터 처리: `category`는 `@Enumerated(STRING)`이라 enum에서 JOB을 빼면 기존 `'JOB'` 행을 읽을 때 `valueOf` 예외가 난다. 그래서 배포 **전에** `UPDATE items SET category='CAREER' WHERE category='JOB'`를 돌리는 순서로 했다(CAREER는 옛 enum에서도 유효해 안전). prod는 그간 AI가 동작 안 해 JOB 행이 0이라 사실상 무피해였다.
- 아쉬운 점: 사용자별로 취업/커리어를 굳이 나눠 보고 싶은 수요가 생기면 다시 갈라야 한다. 다만 그땐 태그 필터로도 충분할 가능성이 높다.

## 21. AI 분류 실패는 ETC가 아니라 미분류(null)로 남긴다

기존엔 AI 호출이 실패하면 `catch`에서 `ETC`로 폴백했다. 그런데 이러면 일시적 실패(무료티어 429 등)가 "진짜 기타"와 구분되지 않고 **영구히 ETC로 잘못 박힌다**. 실제로 모델명 문제로 429나던 동안 저장된 글들이 죄다 ETC로 굳어, 사용자가 "AI가 이걸 기타라고 판단했나?" 오해하게 됐다.

그래서 `GeminiService.classify`가 실패 시 `null`을 반환하고, `ItemClassifier`는 null이면 category를 굳히지 않고 **미분류(null)로 남긴다**. 미분류는 백필(`reclassify-all`)이 자연히 다시 잡으므로, 일시적 실패가 데이터를 오염시키지 않는다. ETC는 모델이 실제로 "기타"라고 판단했을 때만 나온다. 더불어 `reclassify-all`은 미분류뿐 아니라 ETC도 대상으로 넓히고(과거 오염분 + 프롬프트 개선 후 재시도), 재분류 시 기존 태그를 비워 중복 누적을 막는다.

- 이유: "실패"와 "기타"는 의미가 다른데 같은 값으로 뭉개면 데이터가 거짓말을 한다. 실패는 비워둬야 재시도가 가능하다.
- 아쉬운 점: 미분류 아이템은 카테고리 탭 어디에도 안 잡히고 '전체'에만 보인다 — 실패가 잦으면 사용자가 빈 곳을 느낄 수 있다. 무료티어 RPM에 자주 걸리면 백필을 주기적으로 돌리거나 유료 전환이 필요하다.

## 22. 휴면 `aiSummary` 필드를 구현하지 않고 제거

기획 단계에선 아이템마다 AI가 한 줄 요약을 붙이는 그림을 그렸고, 그 흔적으로 `Item.aiSummary` 컬럼·`ItemResponse` 필드·검색 쿼리의 요약 LIKE·아카이브 카드의 요약 표시까지 자리가 잡혀 있었다. 그런데 `ClassificationResult`는 카테고리·태그만 만들고 요약은 생성하지 않아, `aiSummary`는 **쓰는 코드가 한 군데도 없이 읽기만 4곳** — 즉 항상 `null`인 죽은 필드였다.

선택지는 (A) 요약을 실제 구현하거나 (B) 필드를 걷어내는 것이었다. 지금 요약 기능을 새로 붙일 동기가 약하고(분류·태그로 이미 검색이 되고, Gemini 호출당 토큰·레이트도 늘어난다), 죽은 코드가 남아 있으면 "왜 항상 비지?" 하는 혼란만 재생산한다고 봐서 **제거**를 택했다. 엔티티 필드·DTO·검색 LIKE 2곳·프론트 표시 블록을 모두 들어냈다.

- 이유: 항상 null인 필드는 기능이 아니라 부채다. 나중에 요약이 정말 필요해지면 그때 `ClassificationResult`에 필드를 더하는 게 더 명확하다.
- 아쉬운 점: `ddl-auto=update`는 컬럼을 드롭하지 않아 DB의 `ai_summary`는 고아로 남는다(무해). 추후 Flyway 전환 시 `DROP COLUMN`으로 정리한다.

## 23. URL 중복 판정 — 쿼리를 통째로 버리지 않고 추적 파라미터만 제거 (denylist)

처음 `normalizeUrl`은 중복 판정 키를 만들 때 쿼리스트링을 통째로 버렸다. `utm` 같은 추적 파라미터가 붙은 같은 링크를 중복으로 묶으려는 의도였다. 그런데 이러면 쿼리가 *의미를 갖는* 페이지까지 뭉갠다 — 단위 테스트(`ItemProcessorTest`)를 짜다가, 서로 다른 유튜브 영상(`watch?v=AAA` vs `?v=BBB`)이 같은 URL로 정규화돼 두 번째 영상 저장이 `DUPLICATE_URL`로 막히는 걸 발견했다. 검색결과(`?q=`)·페이지네이션(`?page=`)도 같은 문제다.

그래서 쿼리를 보존하되 **추적 파라미터만 골라 제거**하는 denylist 방식으로 바꿨다 (`canonicalizeQuery`). `utm_*` 접두 + `fbclid`·`gclid`·`gbraid`·`msclkid`·`yclid`·`twclid`·`igshid`·`_hsenc`·`mkt_tok` 등 클릭·캠페인 ID들. 이 목록의 근거는 W3C 같은 단일 표준이 아니라 **프라이버시 도구(Brave·Firefox·ClearURLs·AdGuard)가 공통으로 제거하는 집합**에서 고빈도 항목을 추린 것이다.

- 왜 denylist인가: allowlist(사이트별 의미 있는 파라미터만 남김)가 더 정확하지만 사이트마다 룰을 들고 있어야 해 유지비가 크다. 개인 아카이브 규모엔 "알려진 추적값만 제거"가 합리적 트레이드오프다.
- 아쉬운 점: denylist라 새/미등록 추적 파라미터는 통과해 중복 제거가 안 된다. 목록도 수기 관리라 주기적 보강이 필요하다(필요 시 [ClearURLs 룰셋](https://docs.clearurls.xyz) 참고). Curio 실제 저장 데이터로 검증한 게 아니라 일반 목록 기반이다.

## 24. 미인증 요청에 403 대신 401을 반환한다

`@WebMvcTest`로 `ItemController` 인가 규칙을 검증하다가, 토큰 없는 요청이 401이 아니라 **403**으로 나가는 걸 발견했다. 원인은 `SecurityConfig`에 `AuthenticationEntryPoint`를 지정하지 않아 Spring Security 기본값인 `Http403ForbiddenEntryPoint`가 쓰인 것. 그런데 프론트 axios 인터셉터(`client.js`)는 **401에서만** 토큰 재발급(reissue)을 트리거한다. 즉 메모리의 access 토큰이 세션 중간에 만료되면 다음 요청이 403으로 떨어지고, 인터셉터가 이를 무시해 조용한 갱신이 안 된 채 화면이 깨진다. 부팅 시(새로고침) refresh 쿠키로 도는 reissue가 가려줘서 그동안 드러나지 않았다.

그래서 `JwtAuthenticationEntryPoint`를 추가해 미인증 시 **401 + `ApiResponse.error(UNAUTHORIZED)`** 를 돌려주도록 했다(`SecurityConfig.exceptionHandling`에 연결). 인증은 됐지만 권한이 없는 경우(`BOT_USER_NOT_LINKED` 같은 403)는 `CurioException` 경로로 따로 처리돼 이 진입점을 타지 않으므로, 401(미인증)과 403(권한없음)의 의미가 올바르게 갈린다.

- 왜 프론트(403도 reissue)가 아니라 백엔드(401 반환)를 고쳤나: 403은 "인증됐으나 권한 없음"이라는 다른 의미로 이미 쓰고 있어서, 프론트가 모든 403에 reissue를 시도하면 진짜 권한 거부 상황까지 잘못 재발급한다. 401/403을 의미대로 가르는 게 맞다.
- 회귀 방어: `ItemControllerTest`가 미인증 401을 고정한다. prod에서도 `GET /api/v1/items`(토큰 없음) → 401 + `UNAUTHORIZED` 바디로 검증했다.

## 25. 링크 동시 추가 중복은 사전 체크가 아니라 DB 제약으로 막는다

`addLink`은 저장 전에 `existsByUserAndNormalizedUrl`로 중복을 거른다. 그런데 이 "확인 → 저장"은 원자적이지 않다(TOCTOU). 같은 URL을 거의 동시에 두 번 보내면(웹 FAB 더블탭 등) 둘 다 "없음"으로 통과한 뒤 둘 다 insert를 시도하고, 두 번째가 unique 제약(`uk_user_normalized_url`)에 걸려 `DataIntegrityViolationException` → 500이 났다.

확인-저장 사이의 틈은 락이나 직렬화로 없앨 수도 있지만, 1인~소규모 트래픽에 그건 과하다. 대신 **DB의 unique 제약을 최종 심판으로 믿고**, insert에서 터지는 제약 위반을 잡아 `DUPLICATE_URL`(409)로 매핑했다. 사전 `exists` 체크는 정상 경로에서 빠르게 거르는 용도로 남겨둔다(대부분의 중복은 동시성과 무관하게 여기서 걸린다).

- 왜 잡히는 위치가 `save`인가: `Item`이 `@GeneratedValue(IDENTITY)`라 `save` 시점에 즉시 INSERT가 나가므로 예외가 그 자리에서 발생한다(트랜잭션 커밋까지 미뤄지지 않음). 그래서 `addLink` 안의 try/catch로 잡을 수 있다.
- 봇 경로(`process`)는 비동기라 중복이면 조용히 skip하고, 레이스로 제약이 터져도 응답이 없으니 별도 매핑이 불필요하다 — 웹(`addLink`)에서만 매핑한다.
- 회귀 방어: `ItemProcessorAddLinkTest`가 `save`의 `DataIntegrityViolationException` → `DUPLICATE_URL` 매핑을 고정한다.

---

## 26. 필드 길이 초과는 저장 전 truncate로 막되, 자르는 단위와 대상을 가린다

크롤 제목·AI 태그·URL은 외부에서 들어오는 값이라 길이를 보장할 수 없다. 컬럼 한도(`title` 500, `thumbnailUrl` 1000, `originalUrl`/`normalizedUrl` 2000, 태그 `name` 50)를 넘는 값을 그대로 insert하면 MySQL strict 모드에서 `DataIntegrityViolationException`이 나고, 저장이 통째로 롤백돼 500이 난다(예측 버그 백로그 #2). 저장 전에 길이를 맞춰 막기로 했다.

자르는 **위치**는 엔티티의 단일 길목으로 잡았다 — `Item` 빌더/`updateLinkMetadata`, `Tag` 생성자에서 `TextUtils.truncate`로 컷. 이렇게 하면 봇 비동기 저장·웹 `addLink`·재크롤·재분류 등 모든 경로가 한 곳에서 보호된다. 컬럼 길이는 기존 값 그대로를 상수(`Item.TITLE_MAX` 등)로 옮겨 묶었을 뿐이라 `ddl-auto=update`가 스키마를 건드리지 않는다. `normalizedUrl`은 중복 판정 키라 `ItemProcessor`에서도 같은 길이로 잘라 `exists` 체크 값과 저장값이 어긋나지 않게 했다.

자르는 **단위**는 자바 `String.length()`(UTF-16 코드유닛)인데, 여기서 함정이 하나 있었다. `substring`이 경계에서 surrogate pair(이모지 등 보충문자)를 반토막 내면 외톨이 high surrogate가 남고, 이건 유효한 UTF-8이 아니라서 utf8mb4 컬럼에 넣을 때 `Incorrect string value`로 **도리어 500을 일으키거나 데이터를 손상**시킨다. 즉 길이를 막으려던 코드가 거꾸로 깨뜨리는 셈. 그래서 `truncate`는 경계가 surrogate pair 한가운데면 깨진 반쪽까지 버린다. 흩어져 있던 ad-hoc `substring` 절단(`OgCrawlerService.trim`, `processText` 제목, `GeminiService` 프롬프트 입력)도 같은 버그를 안고 있어 전부 `TextUtils.truncate`로 통합했다. (한글·영어는 BMP라 자바 length = MySQL 글자 수로 1:1, 이모지만 자바가 2칸으로 세서 우리가 더 보수적으로 자른다 — 넘칠 일은 없다.)

자르는 **대상**은 "잘라도 의미가 남는" 필드로 한정했다. 제목·본문·태그는 잘려도 쓸모가 있지만, URL은 한 글자라도 깨지면 죽은 링크가 된다. 그래서 `User.profileImageUrl`(현재 길이 미지정=`VARCHAR(255)`)처럼 URL이 한도를 넘을 우려가 있는 곳은 truncate 대상에서 뺐다 — 정말 막아야 하면 자르지 말고 컬럼을 넓히는 게 맞다. 다만 카카오 프로필 URL은 짧아 실제 위험이 낮아 지금은 손대지 않았다.

- 아쉬운 점: 엔티티에 영속화 관심사(컬럼 길이)가 들어왔다. 도메인 순수성보다 "어느 경로로 와도 안 깨진다"는 방어를 택한 결과다. 또 `normalizedUrl`을 2000자에서 자르면 앞부분이 같은 서로 다른 초장문 URL이 같은 것으로 중복 판정될 수 있는데, 현실적으로 거의 없는 극단이라 수용했다.
- 회귀 방어: `TextUtilsTest`(이모지 경계 포함), `entity/ItemLengthTest`(빌더·재크롤·태그 길이 컷), `ItemClassifierTest`(긴 태그명).

---

## 27. SSRF — 외부 URL을 fetch하기 전에 내부/사설 대역을 막는다

`addLink`(웹)과 봇이 받은 임의 URL을 서버가 직접 가져온다 — `OgCrawlerService.crawl`(메타데이터 크롤)과 `S3Service.uploadFromUrl`(이미지 다운로드). 검증이 없으면 공격자가 `http://169.254.169.254/...`(클라우드 메타데이터)·`http://localhost:8080/...`·사설 IP를 넣어 **서버가 대신 내부 자원을 요청**하게 만들 수 있고, 크롤은 그 응답(제목·설명)을 저장해 되돌려주므로 단순 blind SSRF가 아니라 내부 응답을 읽어내는 정보 유출까지 된다(예측 버그 백로그 #3).

`UrlGuard.verifyPublic`을 fetch 직전 공통 길목으로 두고, **http/https 스킴만 허용**한 뒤 호스트를 DNS로 해석해 **나온 IP가 막힌 대역인지** 검사한다(루프백·any-local·링크로컬=메타데이터 포함·사설 10/172.16/192.168·CGNAT 100.64/10·IPv6 ULA·멀티캐스트). 핵심은 **텍스트가 아니라 해석된 IP를 본다**는 점 — 그래서 `http://2130706433/`(십진수)·8진수·16진수 같은 대체 인코딩 우회가 자동으로 막힌다(무엇으로 인코딩됐든 결국 같은 IP로 풀리거나, 안 풀리면 차단). 한 호스트가 여러 IP로 풀리면 하나라도 막힌 대역이면 거절한다.

리다이렉트가 빈틈이라, 크롤은 **Jsoup 자동 리다이렉트를 끄고 직접 따라가며 매 홉을 다시 검증**한다(공개 URL이 302로 내부 IP를 가리키는 우회 차단). 이미지 다운로드는 `HttpClient`를 `Redirect.NEVER`로 두고, 더불어 **10MB 상한과 타임아웃**을 걸었다 — 무제한 다운로드는 내부 fetch가 아니어도 초대용량 URL로 메모리를 터뜨릴 수 있어서다(같은 "서버가 임의 URL을 가져온다" 계열의 DoS).

- 아쉬운 점 / 한계: **DNS rebinding**(검증 때와 fetch 때 IP가 달라지는 공격)은 IP 핀잉을 안 해 못 막는다 — 인증 사용자·소규모 트래픽 기준의 트레이드오프로, 매 홉 재검증으로 흔한 우회만 닫았다. 또 리다이렉트를 직접 따라가며 매 홉 타임아웃이 쌓이면 동기 `addLink`의 스레드 점유가 길어질 수 있는데(악의적 302 체인), 이건 백로그 #5(동기 크롤의 비동기화/타임아웃 예산)에서 근본적으로 다룬다. 호스트가 공개+사설 IP로 동시에 풀리는 split-horizon은 보안 우선으로 차단(드문 과차단 감수).
- 회귀 방어: `UrlGuardTest`(내부/비http 12종 차단·공개 IPv4/IPv6 통과), `OgCrawlerServiceTest`(`crawl`이 내부/비http를 fetch 전 `BLOCKED_URL`로 막는지). IP 리터럴·localhost로 검증해 네트워크를 타지 않는다.

---

## 28. 태그 get-or-create는 예외를 잡지 말고 INSERT IGNORE로 race를 없앤다

AI 분류가 돌려준 태그를 `getOrCreateTag`가 "있으면 읽고 없으면 만든다"로 처리하는데, 이 메서드는 `@Transactional`(웹 `addLink`·봇 `process`·`reclassifyAll`) 안에서 돈다. 기존 구현은 `tagRepository.saveAndFlush(...)`로 insert를 강제하고, 동시에 같은 태그가 들어와 `tags.name` unique 제약이 터지면 `DataIntegrityViolationException`을 잡아 다시 조회해 재사용하는 "낙관적 복구" 패턴이었다. 코드 리뷰에서 이 패턴이 실제로는 복구가 아님을 확인했다 — JPA에서 **flush 예외는 영속성 컨텍스트를 오염시키고 트랜잭션을 rollback-only로 마킹**한다. 예외를 잡아 진행해도 본 트랜잭션의 커밋이 `UnexpectedRollbackException`(500)으로 터지고 **아이템 저장 자체가 유실**된다. `java`·`ai`처럼 흔한 신규 태그를 두 사용자가 거의 동시에 만들면 도달하는, 조용한 동시성 버그였다.

별도 트랜잭션(`REQUIRES_NEW`)으로 태그만 빼서 만드는 흔한 처방도 여기선 안 통한다. MySQL 기본 격리수준이 **REPEATABLE READ**라, 본 트랜잭션은 첫 읽기 시점 스냅샷을 들고 있어 그 뒤 다른(중첩 포함) 트랜잭션이 커밋한 행을 일반 SELECT로는 못 본다. 만든 태그를 본 트랜잭션이 못 읽으면 `@ManyToMany` 연결도 깨진다.

그래서 **예외 경로 자체를 없애는** 쪽으로 갔다. `INSERT IGNORE INTO tags(name)`(`TagRepository.insertIgnore`, 네이티브)로 원자적 insert를 하면 중복이어도 예외 없이 0행 처리돼 트랜잭션이 오염되지 않는다. 직후 재조회는 **잠금 읽기**(`findByNameForUpdate`, `@Lock(PESSIMISTIC_WRITE)` = `SELECT … FOR UPDATE`)로 한다 — 잠금 읽기는 RR 스냅샷을 우회해 '최신 커밋'을 보므로, 내가 방금 넣었든 옆 트랜잭션이 동시에 만들었든 그 행을 확실히 잡는다. 읽어온 태그는 본 트랜잭션 컨텍스트에서 managed라 cascade도 detached 문제 없이 동작한다. 빠른 경로(이미 있는 태그)는 잠금 없는 평범한 `findByName`으로 먼저 거른다.

- 아쉬운 점 / 한계: `INSERT IGNORE`는 **MySQL 전용**이다 — 운영이 MySQL 8로 고정돼 수용했지만, 훗날 DB를 바꾸면 이 한 줄을 다시 봐야 한다. `INSERT IGNORE`가 unique 외의 에러(길이 초과 등)도 조용히 삼키는 점은, 길이를 설계결정 #26에서 이미 truncate로 막아둬 실질 위험이 없다. 진짜 동시성 회귀는 MySQL 통합 테스트라야 재현되는데, 현재 단위(목킹)·슬라이스(H2) 계층을 벗어나 보류했다 — 단위 테스트로는 "충돌 시 `saveAndFlush`가 아니라 `insertIgnore` 경로를 탄다"까지만 박제했다.
- 회귀 방어: `ItemClassifierTest` — 신규 태그가 `insertIgnore` + 잠금 재조회로 확보되는지, 50자 truncate(#26)가 조회·insert에 일관 적용되는지. `@DataJpaTest`(H2)로 새 리포지토리 메서드가 포함된 JPA 컨텍스트 부팅·JPQL 유효성 확인.

---

## 29. 동기 크롤/AI 경로의 시간 예산을 묶는다 — 비동기 전환은 트래픽이 정당화할 때

웹 `addLink`는 동기다(설계 #17 — 저장된 카드를 바로 화면에 띄우려고). 그 요청 스레드가 크롤 + AI 분류 + 저장을 끝까지 붙잡는데, 코드를 보면 **요청당 시간 예산이 사실상 무한**이었다(예측 버그 백로그 #5). 두 군데가 원인이다. ① AI 분류용 `RestTemplate`이 `new RestTemplate()`이라 **타임아웃이 전혀 없었다** — Gemini가 안 답하면 스레드가 영원히 묶인다. ② SSRF 방어(#27)가 리다이렉트를 자동추적 끄고 직접 따라가며 매 홉 재검증하다 보니, 크롤 최악이 6홉 × 5s × 2회 = **최대 60s**까지 늘었다. 느리거나 악의적인 URL 하나가 스레드를 길게(또는 무한히) 점유하고, 그게 쌓이면 Tomcat 풀 고갈 → API 전체 정지로 번진다.

여기서 갈림길은 둘이었다 — (A) 예산을 유한하게 묶기, (B) 동기 구조 자체를 낙관적 비동기(POST가 `PROCESSING` 카드를 즉시 반환하고 크롤·분류는 백그라운드에서 채우는, #17의 "아쉬운 점"이 가리키던 방향)로 바꾸기. **(A)를 택했다.** #5의 실제 위험은 "동기라서"가 아니라 "예산이 무한이라서"다 — 풀 고갈은 수백 개 동시 느린 요청이 있어야 하는데 현재 1인~소규모 트래픽에선 일어나지 않는다. 반면 (B)는 새 상태(`PROCESSING`/`FAILED`)·프론트 폴링·실패 표시·중복체크 시점 이동까지 표면을 크게 늘리는데, 지금 그걸 정당화할 부하가 없고, **나중에 트래픽이 늘면 그때 더해도 되는(되돌릴 일 없는) 변경**이다. 지금 (B)는 과투자다.

(A)의 구체:
- `RestTemplate`에 connect 3s / read 10s(`RestTemplateBuilder`). Gemini 분류엔 10s면 충분하고, 초과하면 `classify`가 예외를 잡아 **null(미분류)로 강등** → `reclassify-all` 백필이 나중에 채운다(#21). 같은 빈을 쓰는 카카오 토큰 교환에도 안전하게 적용된다.
- 크롤에 **전체 wall-clock 데드라인 12s**(`CRAWL_BUDGET_MS`). 재시도 루프는 데드라인 지나면 멈추고, 리다이렉트 홉마다 `timeout = min(5s, 남은 예산)`으로 분배한다. 초과하면 `titleFromUrl` 폴백으로 떨어진다. 이로써 요청당 최악이 크롤 ~12s + 분류 ~10s + DB ≈ **~22s로 유한**해졌다(평소 1~2s는 그대로).

- 아쉬운 점 / 한계: 여전히 동기라 한 요청이 최악 ~22s 스레드를 잡는다 — 풀 고갈 위험을 "무한 → 유한"으로 낮췄을 뿐 0으로 만든 건 아니다. **트래픽이 커지면 (B) 낙관적 비동기가 다음 단계**다(이 결정의 명시적 백로그). 또 크롤 데드라인은 wall-clock 추정이라 단위/슬라이스(H2·목킹) 테스트로는 재현이 어려워 검증을 못 박았다 — 상수 조정으로 남겨둔다.

---

## 30. 카톡 사진 첨부 저장 — 카카오가 주는 명시 신호(media.url)를 IMAGE로 처리한다

"카톡으로 사진을 보내면 저장"은 오래 "알려진 빚"으로 남아 있었다. 전제는 *오픈빌더가 사진을 스킬 서버로 안 넘기거나 넘겨도 DTO가 버린다*였다. 실측해보니(스킬 요청 원본 본문을 임시 로깅) 전제가 틀렸다 — 사진을 보내면 오픈빌더가 **`flow.trigger.type = "IMAGE_UPLOAD"`** 와 함께 **`userRequest.params.media = {type:"image", url: <kakaocdn 이미지 URL>}`** 을 주고, 같은 URL을 `utterance`에도 실어준다. 즉 입력단이 막힌 게 아니었다. 오히려 기존 파이프라인이 우연히 이미 처리하고 있었다 — `detectType`의 이미지 URL 정규식이 `...i_<hash>.png?...`를 매치해 IMAGE로 분기 → `S3Service.uploadFromUrl`이 kakaocdn에서 받아 S3에 올리고 썸네일 → 저장(제목 "이미지"). prod에서 썸네일까지 정상 확인.

문제는 그게 **`utterance`를 이미지 URL 정규식으로 맞히는 우연**에 기댄다는 점이다 — kakaocdn URL이 깔끔한 확장자로 안 끝나거나, 카카오가 utterance에 URL을 안 싣는 쪽으로 바뀌면 링크(웹페이지)로 오인해 엉뚱하게 크롤한다. 그래서 **명시 신호를 우선**하도록 견고화했다. `KakaoSkillRequest`에 `params.media`·`flow`를 추가하고, 컨트롤러가 `media.type=="image"`면 `media.url`을 꺼내 큐에 **타입을 IMAGE로 못박아** 넣는다. 큐 계약을 `enqueue(userId, content, ItemType)`로 넓혀(`null`이면 기존대로 자동 감지) 봇 비동기 경로가 명시 타입을 받는다. 이렇게 하면 URL 형식과 무관하게 사진은 항상 이미지로 저장된다.

별도의 멀티파트 업로드 수신·단기 URL 교환 같은 건 필요 없었다 — 카카오가 공개적으로 접근 가능한 이미지 URL을 주고(`expires`가 붙은 서명 URL), 우리는 그걸 즉시 S3로 복사(`uploadFromUrl`, SSRF 가드 #27·10MB 상한 통과)해 영구 보관하므로 만료도 무관하다.

- 아쉬운 점 / 한계: 사진 제목이 늘 "이미지"로 동일하다(본문/OG가 없어 끌어올 게 없음) — 날짜 접미사나 AI 비전 캡션은 별도 작업으로 남긴다. `media.type`이 image가 아닌 미디어(동영상·파일)는 이미지로 안 잡고 흘려보낸다(현재 범위는 이미지만). 측정에 쓴 원본 본문 로깅 필터는 파악 후 제거했다(사용자 발화가 로그에 남아 상시 운영 부적합).
- 회귀 방어: `KakaoControllerTest` — 실측한 사진 업로드 JSON을 그대로 역직렬화해 `media.url`을 IMAGE로 큐에 넣는지 / 텍스트 발화는 타입 미지정으로 넣는지. `ItemProcessorProcessTest` — 명시 IMAGE면 확장자 없는 URL도 업로드(정규식 의존 X).

---

## 31. 제목/메모 편집을 위해 카드 탭을 상세 시트로 바꾸고, 썸네일 탭만 원문으로 분리한다

아카이브 카드는 탭하면 곧바로 `window.open(원문)` 하나뿐이었다. 그래서 두 가지가 막혔다 — ① 제목을 고칠 자리가 없다(특히 사진은 기본 제목이 "이미지"로 고정, #30의 남은 숙제), ② 사용자가 메모/한마디를 남길 자리가 없다. 둘은 같은 뿌리다 — *카드에 상세/편집 표면이 없다*. 그래서 제목 편집과 메모를 따로 땜질하지 않고 상세 뷰를 하나 만들기로 했다.

선택지는 (A) 탭=상세 시트로 전면 전환(원문 이동은 버튼으로), (B) 썸네일 탭=원문·본문 탭=상세로 분리(하이브리드), (C) 카드에 연필 아이콘만 붙이고 탭=원문 유지. **(B) 하이브리드를 택했다.** 아카이브의 핵심 가치는 "저장한 걸 빨리 다시 여는" 빠름이라 (A)는 그 맛을 죽이고, (C)는 모바일 피드에서 카드가 잡다해지고 메모를 보여줄 자리가 끝내 애매하다. (B)는 둘을 양립시킨다 — 썸네일을 누르면 원문이 바로 열리고(빠름 유지), 본문/제목을 누르면 상세 바텀시트가 떠 제목 인라인 수정·메모·태그·"원문 열기" 버튼을 한곳에 모은다. 모바일에서 탭 한 번에 raw 이미지가 새 탭으로 튀던 실수도 본문 탭에선 사라진다.

데이터·계약 측면의 결정 몇 가지:
- **메모는 `content`와 별도 컬럼**으로 뒀다. `content`는 AI/OG에서 파생된 본문이고 메모는 사용자가 적은 것이라 출처·수명이 다르다. 한 칸을 공유하면 재크롤·재분류가 사용자 메모를 덮어쓸 위험이 생긴다.
- **제목을 직접 고치면 `titleEditedByUser` 플래그**를 세워, 재크롤(`updateLinkMetadata`)이 그 제목을 덮어쓰지 않게 했다. 이게 없으면 사용자가 고친 제목이 "제목 다시 불러오기" 한 번에 날아간다 — 편집 기능의 신뢰를 깨는 사고라 같은 PR에서 막았다.
- **수정은 `PATCH /items/{id}` {title?, memo?} 부분 업데이트** 한 엔드포인트로. 본인 소유가 아니면 다른 조회/삭제와 같게 `ITEM_NOT_FOUND`(존재 비노출), 공백 제목은 `INVALID_INPUT`, 빈 메모는 비우기(null).
- 사진 기본 제목은 "이미지" → **날짜형(`M월 d일 사진`)**으로. 편집을 열어줬으니 본질은 해결됐지만, 안 고쳐도 멀쩡하도록 기본값도 같이 올렸다(#30의 남은 숙제 해소).

- 아쉬운 점 / 한계: 웹에는 여전히 이미지를 추가할 경로가 없다 — 이미지는 봇(카톡 사진)으로만 들어오고, 웹 `addLink`는 항상 LINK로 저장한다(이미지 URL을 붙여도 LINK). 상세가 모달이라 브라우저 뒤로가기·딥링크엔 안 걸린다(목록 한 화면 규모엔 충분). AI 비전 캡션 같은 더 똑똑한 사진 제목은 별도 작업으로 남긴다.

---

## 32. 사진 제목·분류를 AI 비전 호출 한 번으로 채운다

#30·#31을 거치며 사진의 기본 제목은 "이미지" → 날짜형(`M월 d일 사진`)까지 올라왔고 편집 표면도 생겼지만, 두 가지가 여전히 비어 있다 — ① 제목이 날짜로만 동일해 *무엇이 찍힌 사진인지*는 못 담는다, ② 이미지는 분류할 텍스트가 없어 `ItemClassifier`가 AI 호출 없이 `ETC`로 박고 태그도 안 단다. 둘은 #31에서 본 것과 같은 뿌리다 — *이미지에는 AI에 줄 텍스트가 없다*. 그래서 제목과 분류를 따로 손대지 않고, 입력을 텍스트가 아니라 **이미지 자체**로 바꿔 한 번에 푼다.

선택지는 (A) 비전 멀티모달 호출로 한 줄 제목 + category/tags를 한 번에 생성, (B) 비전 없이 날짜형 제목 유지 + 사용자 수동 정리에 맡김, (C) 캡션으로 제목만 만들고 분류는 ETC 유지. **(A)를 택했다.** gemini-2.5-flash가 멀티모달이라 `generateContent`에 `inlineData{mimeType, base64}` 파트만 더하면 추가 모델 없이 텍스트 분류와 같은 호출 한 번으로 제목·분류를 같이 받는다. (C)는 호출 비용을 쓰고도 #20에서 통합한 카테고리·태그가 이미지에만 비는 비대칭을 남기고, (B)는 지금 상태 그대로라 개선이 아니다.

데이터·정책 측면:
- **이미지 바이트는 새로 받지 않고 재사용**한다. `S3Service.uploadFromUrl`이 이미 kakaocdn에서 받아 S3에 올리므로(#30), 그 바이트를 분류에 그대로 흘려 추가 외부 요청·SSRF 표면을 안 늘린다.
- **폴백은 기존 원칙 그대로**. 비전 실패·키 없음·쿼터 초과면 제목은 날짜형(#30), category는 ETC로 굳히지 않고 미분류(null)로 남겨 `reclassify-all`이 다시 잡게 한다(#21). 일시적 실패를 영구 오염시키지 않는다.
- **사용자가 고친 제목은 안 덮어쓴다**. #31의 `titleEditedByUser` 가드를 비전 캡션에도 그대로 적용 — AI가 나중에 돌아도(백필 포함) 사용자가 손댄 제목은 보존한다.
- 이미 `ETC`·날짜제목으로 쌓인 사진의 재처리는 `reclassify-all`을 이미지도 비전에 태우도록 넓혀(수정 안 한 것만) 백필한다. prod 이미지 수가 적으면 생략 가능.

프라이버시 트레이드오프(이 결정의 핵심): 무료 Gemini 티어는 보낸 내용을 모델 학습에 쓸 수 있다(#19에서 기록한 한계). 지금까지는 링크 제목·텍스트만 보냈지만, 이제 **사용자 사진 원본 바이트**를 외부 AI로 보낸다 — 더 민감한 데이터다. 그럼에도 (A)로 가는 근거: 본 서비스는 본인이 *스스로 저장한* 개인 아카이브이고, 이미 #30에서 같은 사진을 kakaocdn↔S3로 외부 경유시키고 있으며, 비전 없이 사진을 "이미지"로만 쌓는 건 아카이브의 효용을 크게 깎는다. 단 이 수용은 "사진을 외부 AI에 보낸다"는 사실을 *명시적으로 인지한 위에서의 선택*이고, 데이터 민감도가 문제되면 #19 때처럼 유료/온프레미스 비전으로 공급자만 갈아끼우면 된다(파이프라인은 그대로). 추후 사용자 고지(설정 토글 등)는 별도 과제로 남긴다.

- 아쉬운 점 / 한계: 동기 경로(웹)에는 아직 이미지 추가 자체가 없어(#31) 비전은 당분간 봇 비동기 경로에서만 돈다. 비전 호출은 텍스트보다 토큰·레이턴시가 크지만 봇은 비동기라 사용자 체감엔 영향이 적다(무료티어 모델별 쿼터는 #19 메모 참조). 캡션 품질은 모델에 의존하므로 어색하면 사용자가 #31 편집으로 고치는 길을 그대로 둔다.

---

## 봇 연동 코드 흐름 (참고)

웹앱과 카카오 봇 사용자를 잇는 방법. 카카오 `botUserKey`만으로는 우리 유저가 누군지 모른다.

1. 웹앱 로그인 후 `GET /user/link-code` → Redis에 6자리 코드 저장 (TTL 10분, `LinkCodeService`)
2. 사용자가 그 코드를 카카오 채널에 전송
3. `KakaoController`가 코드를 보고 `botUserKey ↔ userId`를 연결
4. 이후 보내는 링크/텍스트는 자동 저장

코드는 만료가 짧고 일회성이라, 노출돼도 위험이 제한적이다.
