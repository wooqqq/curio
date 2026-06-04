# 설계 결정 기록

코드만 봐서는 알기 어려운 "왜 이렇게 했는지"를 모아둔 문서. 무엇을 만들었는지는 `CLAUDE.md`, 진행 순서는 `PROGRESS.md`를 본다.

각 항목은 *상황 → 선택 → 이유 → 아쉬운 점* 순으로 적었다.

---

## 1. 카카오 로그인을 Spring Security OAuth2 Client로 안 하고 RestTemplate으로 직접 호출

처음엔 당연히 Spring Security의 OAuth2 Client를 쓰려고 했다. 그런데 카카오 앱에서 client_secret을 켜둔 상태에서 OAuth2 Client가 토큰 요청에 PKCE를 자동으로 붙였고, 카카오가 이 조합을 거절했다. 클라이언트 인증 방식을 `none`으로 두면 PKCE가 자동 활성화되는 구조라 우회가 까다로웠다.

결국 토큰 요청 파라미터를 완전히 통제하려고 `RestTemplate`으로 카카오 토큰/유저 API를 직접 호출하는 방식으로 바꿨다 (`KakaoAuthService`). Spring Security는 OAuth 흐름에서 빼고, **JWT 필터 + 인가 규칙** 용도로만 남겼다 (`SecurityConfig`는 `STATELESS`, `JwtFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록).

- 장점: 카카오 쪽 요청을 우리가 직접 다루니 디버깅이 명확하다.
- 아쉬운 점: OAuth2 Client가 자동으로 해주던 부분(state 검증 등)을 직접 챙겨야 한다. 다른 소셜 로그인을 붙이면 보일러플레이트가 늘어난다.

## 2. JWT Access/Refresh + Refresh를 DB에 저장 (rotation)

Access는 짧게, Refresh로 재발급하는 표준 구조. 다만 Refresh 토큰을 stateless하게 두지 않고 `refresh_tokens` 테이블에 저장한다.

이유는 두 가지다. 재발급 시 기존 Refresh를 지우고 새로 발급하는 **rotation**을 하려면 서버가 "현재 유효한 Refresh"를 알아야 하고(`AuthService.reissue`에서 기존 토큰 삭제 후 재발급), 로그아웃 시 서버에서 토큰을 무효화해야 하기 때문이다(`logout`에서 유저의 Refresh 삭제). 순수 stateless JWT로는 로그아웃·강제 만료가 안 된다.

- 아쉬운 점: 재발급마다 DB 조회/쓰기가 든다. 트래픽이 커지면 Redis로 옮기는 게 맞다.
- 알려진 빚: 로그인 콜백에서 토큰을 URL 쿼리로 프론트에 넘긴다(`/auth/callback?accessToken=...`). 배포 전에 httpOnly 쿠키나 일회성 code 교환으로 바꿔야 한다.

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

---

## 봇 연동 코드 흐름 (참고)

웹앱과 카카오 봇 사용자를 잇는 방법. 카카오 `botUserKey`만으로는 우리 유저가 누군지 모른다.

1. 웹앱 로그인 후 `GET /user/link-code` → Redis에 6자리 코드 저장 (TTL 10분, `LinkCodeService`)
2. 사용자가 그 코드를 카카오 채널에 전송
3. `KakaoController`가 코드를 보고 `botUserKey ↔ userId`를 연결
4. 이후 보내는 링크/텍스트는 자동 저장

코드는 만료가 짧고 일회성이라, 노출돼도 위험이 제한적이다.
