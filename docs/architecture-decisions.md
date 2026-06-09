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

---

## 봇 연동 코드 흐름 (참고)

웹앱과 카카오 봇 사용자를 잇는 방법. 카카오 `botUserKey`만으로는 우리 유저가 누군지 모른다.

1. 웹앱 로그인 후 `GET /user/link-code` → Redis에 6자리 코드 저장 (TTL 10분, `LinkCodeService`)
2. 사용자가 그 코드를 카카오 채널에 전송
3. `KakaoController`가 코드를 보고 `botUserKey ↔ userId`를 연결
4. 이후 보내는 링크/텍스트는 자동 저장

코드는 만료가 짧고 일회성이라, 노출돼도 위험이 제한적이다.
