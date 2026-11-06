# 가이드 04 · XSS · CSRF · CORS · 보안 헤더

> **한 줄 요약**: 3일차 게시판 서버에 **비밀번호 변경 API**를 붙이고, 브라우저를 노리는 공격 두 가지(**저장형 XSS**, **CSRF**)를 **먼저 직접 성공시킨 뒤** 막음. 이어서 **CORS가 무엇을 막고 무엇을 못 막는지** 확인하고, 응답에 **보안 헤더(CSP)**를 붙이는 날임

## 목표
- 게시글 제목에 넣은 스크립트가 **다른 사람의 브라우저에서** 실행되는 것을 봄 (저장형 XSS) → 화면 한 줄로 막음
- 비밀번호를 모르는 공격자 사이트가 **내 세션 쿠키만으로** 비밀번호를 바꾸는 것을 봄 (CSRF) → CSRF 토큰으로 막음
- CSRF 보호를 켜면 **로그인까지 막히는 이유**를 알고, 토큰을 받아 오는 API를 만들어 해결함
- CORS는 "요청을 막는 것"이 아니라 "**응답을 못 읽게 하는 것**"임을 확인하고, `*`로 넓게 열면 무슨 일이 생기는지 봄
- 스프링 시큐리티가 **기본으로 붙여 주는 보안 헤더**를 보고, CSP를 하나 더 붙임

## 완료 기준
| 공격 / 확인 | 오늘 시작할 때 | 오늘 끝났을 때 |
|---|---|---|
| 제목이 `<img src=x onerror="alert(...)">`인 글 | 화면을 열 때마다 경고창 | 제목이 **글자 그대로** 보임 |
| `localhost:4000` "경품 받기" (user 로그인 상태) | `{"changed":"user"}` → 비밀번호가 `hacked`로 바뀜 | **403** `{"error":"CSRF 토큰이 없거나 틀립니다"}` |
| React 화면 로그인 · 글쓰기 | 200 · 201 | 200 · 201 (요청 헤더에 `X-CSRF-TOKEN`) |
| `localhost:4000` "내 정보 훔쳐보기" | `읽기 실패: Failed to fetch` | 그대로 (`allowedOriginPatterns("*")`로 바꾸면 `200 {"username":"user"}` → 되돌림) |
| `curl.exe -i http://localhost:8080/api/posts` 응답 헤더 | `X-Frame-Options: DENY` 등 4개 | + `Content-Security-Policy: default-src 'self'; script-src 'self'` |

- [ ] 4일차 원본 테스트 5개가 확인하는 내용(CSRF 403 · 토큰으로 통과 · 로그인도 토큰 필요 · 보안 헤더 · CORS 사전 요청)이 위 표와 같음

## 만들 API
| 요청 | 설명 | 누가 쓸 수 있나 |
|---|---|---|
| `POST /account/password` | 비밀번호 변경. 폼 본문 `newPassword=...`. **오늘 공격 대상** | 로그인한 사람 |
| `GET /api/public/csrf` | CSRF 토큰 받기 | 누구나 |
| 3일차 API | 공지 · 내 정보 · 관리자 통계 · 회원가입 · 회원 목록 · 로그인 · 로그아웃 · 게시판 5개 | 3일차와 같음 (단, 4-6부터 GET이 아닌 요청은 **CSRF 토큰 필요**) |

응답 예시:
```json
// POST /account/password   newPassword=hacked   (user로 로그인)
{"changed": "user"}
// GET /api/public/csrf     (token 값은 부를 때마다 다름)
{"parameterName": "_csrf", "token": "TAjLw8rm...UDzCDUkjmGyPG9y", "headerName": "X-CSRF-TOKEN"}
// 토큰 없이 POST · PUT · DELETE (4-7 이후)
{"error": "CSRF 토큰이 없거나 틀립니다"}               // 403
```
- 계정: `user` · `kim`(USER), `admin`(ADMIN). 비밀번호는 모두 `1234`. 서버가 뜰 때 글 2개(1번 user의 글, 2번 kim의 글)가 들어감
- **공격 코드는 이 실습 환경(내 PC의 localhost)에서만 실행함**

---

## 1. 개념 먼저

### 1-1. 오늘의 등장인물: 출처(Origin) 세 개
| 주소 | 누구 | 비유 |
|---|---|---|
| `http://localhost:8080` | 우리 서버 (스프링) | 은행 |
| `http://localhost:3000` | 우리 화면 (React) | 은행 앱 |
| `http://localhost:4000` | **공격자 사이트** (`evil-site/index.html`) | 은행인 척하는 경품 사이트 |

- **출처(Origin)** = `프로토콜 + 호스트 + 포트`. 셋 중 하나라도 다르면 다른 출처임 → 3000과 4000은 **다른 출처**
- 그런데 **쿠키는 포트를 구분하지 않음.** `localhost:8080`이 준 세션 쿠키(`JSESSIONID`)는 3000에서 보내든 4000에서 보내든 `localhost:8080`으로 가는 요청에 **브라우저가 알아서 실어 줌.** 오늘 공격 두 개가 모두 이것을 이용함

### 1-2. XSS (사이트 간 스크립팅): 남의 브라우저에서 내 스크립트 돌리기
```
kim이 글 제목에 <img src=x onerror="..."> 저장
   → user가 게시판을 엶 → 화면이 제목을 "HTML로" 넣음
   → 없는 그림(x)이라 onerror가 실행됨 → kim의 스크립트가 user의 브라우저에서, user의 권한으로 돌아감
```
- **저장형 XSS**: 공격 코드가 DB에 저장돼서 그 글을 보는 **모든 사람**이 피해자가 됨 (오늘 실습)
- 원인은 서버가 아니라 **"입력을 HTML로 출력한 것"**. React의 `{p.title}`은 값을 **글자로** 넣음(이스케이프: `<` → `&lt;`). 그런데 `dangerouslySetInnerHTML`은 이름 그대로 "위험하게 HTML로" 넣음
- **HttpOnly 쿠키**: 스크립트(`document.cookie`)로 읽을 수 없는 쿠키. 스프링의 `JSESSIONID`는 기본이 HttpOnly → XSS로 쿠키를 **훔치지는** 못함
  - 하지만 스크립트는 쿠키를 몰라도 **user의 세션으로 요청을 보낼 수 있음** (4-3에서 직접 봄). HttpOnly는 피해를 줄일 뿐 XSS를 막지 못함

### 1-3. CSRF (사이트 간 요청 위조): 남의 쿠키로 요청 보내게 하기
```
user가 3000에서 로그인 (브라우저에 JSESSIONID 쿠키가 생김)
   → 같은 브라우저로 4000(공격자 사이트)에 들어가 "경품 받기" 클릭
   → 숨겨진 폼이 POST http://localhost:8080/account/password  newPassword=hacked 를 보냄
   → 브라우저가 쿠키를 자동으로 실어 줌 → 서버: "로그인한 user의 정상 요청이네" → 비밀번호 변경
```
- 공격자는 비밀번호도, 쿠키 값도 **몰랐음.** 브라우저가 쿠키를 자동으로 보낸 것뿐임
- 그래서 서버는 "쿠키가 있나?"만으로는 **진짜 우리 화면에서 온 요청인지** 구분하지 못함
- 2일차에 회원가입이 막혔던 원인이 이 보호 기능(CSRF 필터)이었고, 그때 `csrf.disable()`로 껐음. 오늘 다시 켬

### 1-4. CSRF 토큰: "우리 화면만 아는 비밀 번호표"
```
React: GET /api/public/csrf → {"token":"abc...","headerName":"X-CSRF-TOKEN"}  (서버는 세션에 원본 토큰 저장)
React: POST /api/posts   헤더 X-CSRF-TOKEN: abc...   → 서버: 세션의 토큰과 맞음 → 통과
공격자 폼: POST /account/password (토큰 없음)          → 서버: 403
```
- 토큰은 **세션마다 하나**임. 공격자 사이트는 쿠키를 실어 보낼 수는 있어도 **토큰 값을 모름**
  - "공격자도 `/api/public/csrf`를 부르면 되지 않나?" → 부를 수는 있지만 **다른 출처라서 응답을 읽을 수 없음** (1-5 CORS)
- CSRF 보호는 **GET · HEAD · OPTIONS · TRACE를 뺀 모든 요청**에 토큰을 요구함. "상태를 바꾸는 요청"이기 때문. **로그인(`POST /api/login`)도 예외가 아님** (4-6에서 직접 봄)
- 토큰 값은 부를 때마다 달라 보임. 원본 토큰은 같지만 **매번 다르게 가려서(마스킹)** 내주기 때문 (응답 길이로 토큰을 추측하는 공격 방지). 어느 값을 보내도 통과함
- `CsrfToken token` 파라미터: 컨트롤러 메서드에 이렇게 쓰면 스프링 시큐리티가 **지금 세션의 토큰**을 넣어 줌. 그대로 돌려주면 JSON이 됨

### 1-5. CORS: "요청은 가지만, 응답은 못 읽음"
- **동일 출처 정책(SOP)**: 브라우저의 기본 규칙. 스크립트(`fetch`)는 **다른 출처의 응답을 읽을 수 없음**
- **CORS**: 서버가 "이 출처는 내 응답을 읽어도 됨"이라고 허락하는 방법. 1일차 `WebConfig`의 `allowedOrigins("http://localhost:3000")`가 이것임
- 오늘 꼭 기억할 것 세 가지
  1. CORS는 **브라우저가 지키는 규칙**임. `curl`에는 아무 효과가 없음
  2. CORS는 **응답 읽기**를 막음. **요청 보내기**는 못 막음 → 응답이 필요 없는 공격(1-3의 폼 POST)은 CORS로 못 막음. 그래서 CSRF 토큰이 따로 필요함
  3. `allowCredentials(true)` + 아무 출처나 허락(`allowedOriginPatterns("*")`) = **어느 사이트든 로그인한 사용자의 정보와 CSRF 토큰까지 읽어 감** (4-8에서 직접 봄)
- 스프링은 허락 안 된 출처의 요청을 서버에서도 `403 Invalid CORS request`로 거절함 (CORS 설정이 걸린 `/api/**` 주소만). `/account/**`는 CORS 설정이 없어서 서버가 그대로 처리함 → 그래서 오늘 공격 대상을 `/account/password`에 둠

### 1-6. 보안 헤더: 브라우저에게 주는 안전 수칙
스프링 시큐리티는 설정을 안 해도 응답에 아래 헤더를 붙임 (`HeaderWriterFilter`, 1일차 필터 목록에 있던 것).

| 헤더 | 뜻 |
|---|---|
| `X-Content-Type-Options: nosniff` | `Content-Type`을 무시하고 내용을 추측해서 실행하지 말 것 |
| `X-Frame-Options: DENY` | 이 페이지를 다른 사이트의 `<iframe>` 안에 넣지 말 것 (클릭재킹 방지) |
| `Cache-Control: no-cache, no-store, ...` | 로그인한 사람의 응답을 캐시에 남기지 말 것 |
| `X-XSS-Protection: 0` | 옛 브라우저의 XSS 필터를 끔 (그 필터가 오히려 구멍이 돼서 지금은 끄는 것이 권장) |
| `Strict-Transport-Security` (HSTS) | 앞으로 HTTPS로만 접속할 것. **HTTPS 응답에만** 붙음 → 오늘(HTTP)은 안 보임 |
| `Content-Security-Policy` (CSP) | 이 페이지에서 **실행해도 되는 스크립트 출처**. 기본으로는 안 붙음 → 4-9에서 붙임 |

- CSP `default-src 'self'; script-src 'self'`: 같은 출처의 파일만 불러오고 실행함. `onerror="..."` 같은 **인라인 스크립트도 막음** → XSS가 생겨도 피해를 줄이는 **두 번째 방어선**
- 단, CSP는 **HTML을 내려주는 응답**에 붙어야 효과가 있음. 지금 HTML은 Vite(3000)가 내려주고, 8080은 JSON만 줌 → 오늘은 "헤더가 붙는 것"까지만 확인함 (6장 한계)

### 1-7. 용어 정리
| 용어 | 쉬운 뜻 |
|---|---|
| 출처 (Origin) | 프로토콜 + 호스트 + 포트. 하나라도 다르면 다른 출처 |
| XSS | 남의 화면에 내 스크립트를 넣어 실행시키는 공격 |
| 저장형 XSS | 공격 코드가 DB에 저장돼 보는 사람마다 실행되는 XSS |
| 이스케이프 | `<`를 `&lt;`처럼 바꿔 HTML이 아니라 글자로 보이게 하는 것 |
| `dangerouslySetInnerHTML` | React에서 문자열을 HTML로 넣는 속성. 이스케이프를 안 함 |
| HttpOnly | 스크립트로 못 읽는 쿠키 표시 |
| CSRF | 사용자의 쿠키를 이용해 사용자가 원하지 않은 요청을 보내게 하는 공격 |
| CSRF 토큰 | 우리 화면만 받아 갈 수 있는 비밀 값. 상태를 바꾸는 요청에 같이 보냄 |
| `CsrfFilter` | 토큰을 검사하는 스프링 시큐리티 필터. 틀리면 `CsrfException` → 403 |
| 동일 출처 정책 (SOP) | 다른 출처의 응답은 스크립트가 못 읽는다는 브라우저 기본 규칙 |
| CORS | 서버가 "이 출처는 내 응답을 읽어도 됨"이라고 허락하는 방법 |
| 사전 요청 (preflight) | 본 요청 전에 브라우저가 `OPTIONS`로 "보내도 돼?"를 묻는 요청 |
| CSP | 실행해도 되는 스크립트 출처를 브라우저에 알려 주는 헤더 |

---

## 2. 미리 바꿔둔 설정
### 2-1. 3일차에서 가져온 파일
4일차는 **3일차 완성 코드를 고치고 늘리는 날**임. 그래서 내가 쓴 `day03` 코드를 `day04`로 미리 복사해 둠.

| 파일 | 상태 | 오늘 할 일 |
|---|---|---|
| `ApiController` · `MemberRepository` · `JpaUserDetailsService` · `Post` · `PostRepository` · `PostController` · `PostGuard` · `DataInitializer` | 3일차 코드 그대로 | **안 고침** (그대로 씀) |
| `PostService` | 3일차 코드 그대로 | **고침**: 4-1 (3일차에서 빠진 한 줄) |
| `SecurityConfig` | 3일차 코드 그대로 | **고침**: 4-1 (빠진 규칙 한 줄), 4-6 CSRF 켜기, 4-7 거절 이유, 4-9 CSP |
| `Member` · `MemberController` | 3일차 코드 그대로 | **고침**: 4-2 비밀번호 변경 |
| `WebConfig` | 3일차 코드 그대로 | 4-8에서 **잠깐 바꿨다가 되돌림** |
| `SecurityDay4Application` | 3일차 것에서 **이름만 바꿔** 만들어 둠 | 안 고침 |
| `CsrfController` | **빈 파일** (역할 주석만) | **새로 씀**: 4-7 |

- **3일차 코드에 빠진 곳이 두 군데 있음** (Claude Code가 서버를 돌려 확인함). 4-1의 두 줄은 Claude Code가 채워 둠 (2026-11-06). 4-1은 읽고 확인만 하면 됨
- 가져온 파일의 주석에 옛 가이드의 단계 번호(`(4-4)` 등)가 남아 있음. 동작과는 상관없음
- 3일차 폴더(`day03`)는 그대로 남아 있음. 4일차에서 고쳐도 3일차 코드는 안 바뀜

### 2-2. 설정 파일
- `build.gradle`: 3일차와 같음 (웹 · 시큐리티 · JPA · H2)
- `settings.gradle`: 프로젝트 이름이 `security-day4`
- `application.properties`: `spring.application.name=security-day4` 한 줄

### 2-3. 화면 (`day04/frontend`)
3일차 화면과 **세 가지가 다름**. 오늘은 4-4에서 **한 줄을 직접 고침** (바꾸기 전후 차이를 보는 것이 실습이라서).

| 바뀐 것 | 내용 |
|---|---|
| 제목 칸 | `dangerouslySetInnerHTML`로 제목을 **HTML로** 넣음 (XSS 구멍, 일부러). 입력 칸 안내도 `HTML 가능: <b>굵게</b>` |
| CSRF 토큰 | GET이 아닌 요청 전에 `GET /api/public/csrf`로 토큰을 받아 헤더에 실음 (`App.tsx`의 `csrfHeader()`). 토큰 API가 없으면(오늘 시작 상태) 토큰 없이 그냥 보냄 |
| 버튼 | 3일차의 **수정 · 전체 삭제 버튼이 없음.** 글쓰기 · 새로고침 · 삭제만 있음 |

- **`frontend/evil-site/index.html`**: 실습용 공격자 사이트. 버튼 두 개
  - **경품 받기**: 숨겨진 입력 칸(`newPassword=hacked`)이 든 폼을 `http://localhost:8080/account/password`로 POST (4-5 CSRF)
  - **내 정보 훔쳐보기**: `fetch('http://localhost:8080/api/me', {credentials:'include'})`로 로그인한 사람의 정보를 읽으려 함 (4-8 CORS)

### 2-4. IntelliJ
- `day04/backend/build.gradle`을 Gradle 프로젝트로 연결함 → [README "IntelliJ에서 Gradle 프로젝트 연결하기"](README.md#intellij에서-gradle-프로젝트-연결하기)

---

## 3. 실행 방법과 확인 도구
4장을 시작하기 전에 한 번 읽어 둠. 오늘은 **터미널을 세 개** 씀.

### 3-1. 서버 · 화면 · 공격자 사이트 켜기
**3일차 서버가 켜져 있으면 먼저 끔** (같은 8080 포트를 씀).
```powershell
# 터미널 1 — 서버 (저장소 루트에서)
cd day04\backend
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat bootRun
```
```powershell
# 터미널 2 — 화면 localhost:3000 (저장소 루트에서, npm install은 처음 한 번만)
cd day04\frontend
npm install
npm run dev
```
```powershell
# 터미널 3 — 공격자 사이트 localhost:4000 (저장소 루트에서). 터미널 2가 뜬 뒤에 켬
cd day04\frontend
npx vite evil-site --port 4000
```
- 서버가 뜬 신호: `Started SecurityDay4Application`
- **화면(터미널 2)을 먼저 켜고 공격자 사이트(터미널 3)를 나중에 켬.** 두 개가 같은 `node_modules`를 쓰기 때문에 순서가 바뀌면 3000 화면이 하얗게 나올 수 있음 (5장 "막히면")
- `http://localhost:3000`에 게시판, `http://localhost:4000`에 "축하합니다! 경품 이벤트에 당첨되었습니다" 화면이 뜨면 준비 끝

### 3-2. 재시작하면 처음으로 돌아감
- H2 메모리 DB라 **서버를 재시작하면 글 2개, 비밀번호 `1234`로 돌아옴.** XSS 글을 지우거나 바뀐 비밀번호를 되돌릴 때 재시작함
- 세션도 사라지므로 재시작 뒤에는 **화면에서 다시 로그인**함
- **화면(3000)과 공격자 사이트(4000)는 재시작하지 않아도 됨.** 서버만 재시작함

### 3-3. 화면 읽는 법
- 맨 위: 아이디 · 비밀번호, **로그인 · 로그아웃**, `현재: (로그인한 사람)`
- 가운데: 새 글 제목 칸, **글쓰기 · 새로고침 · 결과 지우기**
- 글 목록 표: 번호 · 제목 · 작성자 · **삭제** 버튼
- 맨 아래 결과표: `요청 | 상태 | 응답`. **최신 요청이 맨 위**
  - CSRF 토큰을 받는 요청(`GET /api/public/csrf`)은 결과표에 **안 나옴**. F12 → Network에서 보임
- 4장의 "결과"는 이 결과표에 나오는 모양 그대로 적었음. 날짜(`timestamp`)와 토큰 값은 매번 다름

### 3-4. F12 개발자 도구
오늘은 F12를 많이 씀. 브라우저에서 `F12` → 위쪽 탭
- **Console**: 스크립트 오류, **CORS 오류**(`blocked by CORS policy`)가 빨간 줄로 나옴
- **Network**: 요청 하나를 누르면 **Headers** 탭에 요청 헤더(`X-CSRF-TOKEN`, `Cookie`)와 응답 헤더가 보임
- **Application → Cookies → `http://localhost:3000`**: `JSESSIONID` 줄의 **HttpOnly** 칸에 체크가 있음 (4-3)

### 3-5. 터미널로 확인하기 (선택)
- `curl`은 브라우저가 아니라서 **CORS가 상관없고**, 쿠키도 자동으로 안 보냄
- **4-6부터는 curl로 POST · PUT · DELETE를 보내도 CSRF 토큰이 필요함** (Basic 로그인이어도). 토큰은 세션에 묶여 있어서 쿠키 파일(`jar.txt`)로 세션을 이어 가야 함
```powershell
# 응답 헤더 보기 (4-9)
curl.exe -i http://localhost:8080/api/posts

# 토큰을 받아서 글쓰기 (4-7 이후)
$t = (curl.exe -s -c jar.txt http://localhost:8080/api/public/csrf | ConvertFrom-Json).token
curl.exe -i -b jar.txt -u kim:1234 -X POST http://localhost:8080/api/posts -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $t" -d '{\"title\":\"curl-token\"}'
```
- `-c jar.txt`: 받은 쿠키를 파일에 저장, `-b jar.txt`: 파일의 쿠키를 보냄
- Windows PowerShell 5.1에서는 `-d '...'` 안에 **띄어쓰기를 넣지 않음.** 띄어쓰기에서 본문이 잘려 `400 Bad Request`가 남 (그래서 제목을 `curl-token`으로 씀)

---

## 4. 단계별 진행
- 작업 폴더: `day04/backend` (4-4만 `day04/frontend`)
- 3일차 파일은 이미 가져와 있음 (2-1). 오늘은 **새 파일 1개를 쓰고, 가져온 파일 5개를 고침**
- 단계마다 맨 위에 **목적**(왜 고치나)과 **수정**(어느 파일의 어디를)을 적었음. 먼저 읽고 코드를 씀
- **"공격 성공 → 막기"** 순서가 반복됨. 공격 단계(4-3, 4-5)는 코드를 안 쓰고 **일부러 뚫리는 것을 봄**
- 결과가 다르면 다음 단계로 가지 말고 5장 "막히면"을 봄

```
day04/backend/src/main/java/com/kyobo/lab/
├── ApiController · MemberRepository · JpaUserDetailsService       (가져옴, 그대로)
├── Post · PostRepository · PostController · PostGuard · DataInitializer (가져옴, 그대로)
├── SecurityDay4Application.java                                  (가져옴, 이름만 바꿈)
├── PostService.java          (가져옴 → 4-1에서 고침)
├── SecurityConfig.java       (가져옴 → 4-1, 4-6, 4-7, 4-9에서 고침)
├── Member.java · MemberController.java   (가져옴 → 4-2에서 고침)
├── WebConfig.java            (가져옴 → 4-8에서 잠깐 바꿨다 되돌림)
└── CsrfController.java       (빈 파일 → 4-7에서 새로 씀)
day04/frontend/src/App.tsx    (4-4에서 한 줄 고침)
```

| 단계 | 할 일 | 보게 될 것 |
|---|---|---|
| 4-1 | 3일차 코드 바로잡기 (두 줄) | 공지 · 회원가입 다시 됨, 관리자 전체 삭제가 진짜 지움 |
| 4-2 | 비밀번호 변경 API (공격 대상) | `{"changed":"user"}` |
| 4-3 | 저장형 XSS 공격 (코드 없음) | 경고창, 남의 글이 지워짐 (일부러) |
| 4-4 | XSS 막기 (화면 한 줄) | 제목이 글자 그대로 |
| 4-5 | CSRF 공격 (코드 없음) | 비밀번호가 `hacked`로 바뀜 (일부러) |
| 4-6 | CSRF 보호 켜기 | 공격 403, 그런데 **로그인도 403** |
| 4-7 | 토큰 API + 거절 이유 | 로그인 200, 공격 `CSRF 토큰이 없거나 틀립니다` |
| 4-8 | CORS가 막는 것 / 잘못 열면 | 훔쳐보기 실패 → `*`로 열면 성공 → 되돌림 |
| 4-9 | 보안 헤더 + CSP | `Content-Security-Policy` 헤더 |

---

### 4-1. 3일차 코드 바로잡기

> **목적**: 3일차 코드에서 빠진 두 줄을 채움. 하나는 오늘 만들 **토큰 API(`/api/public/csrf`)가 로그인 없이 열려야** 해서 꼭 필요하고, 하나는 관리자 전체 삭제가 **"200인데 아무것도 안 지우는"** 문제임
>
> **수정**: 가져온 `SecurityConfig.java` — `authorizeHttpRequests` 안에 **한 줄 추가** / 가져온 `PostService.java` — `deleteAll()` 안에 **한 줄 추가**
>
> **이미 채워 둠**: 이 두 줄은 Claude Code가 넣어 두었음. 아래 "할 일 전에" 표는 고치기 전 모습이니 읽기만 하고, 코드에 두 줄이 있는지 본 뒤 "확인" 표대로 나오는지만 봄

#### 할 일 전에: 지금 상태를 먼저 봄
서버를 켜고 터미널에서 확인함.
```powershell
curl.exe -i http://localhost:8080/api/public/notices
curl.exe -i -u admin:1234 -X DELETE http://localhost:8080/api/posts
curl.exe http://localhost:8080/api/posts
```
| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/public/notices` | **401** | `{"error":"로그인이 필요합니다"}` ← 누구나 봐야 하는 공지가 막힘 |
| `DELETE /api/posts` (admin) | 200 | `{"deleted":"all"}` |
| `GET /api/posts` | 200 | `[{"id":1,"title":"user의 첫 글","author":"user"},{"id":2,"title":"kim의 첫 글","author":"kim"}]` ← **안 지워짐** |

- 원인 ①: 3일차 `SecurityConfig`에 `.requestMatchers("/api/public/**").permitAll()` 줄이 없음 → `/api/public/**`가 `anyRequest().authenticated()`에 걸림 (회원가입도 401)
- 원인 ②: 3일차 `PostService.deleteAll()`의 몸통이 비어 있음 → 권한 검사는 통과하는데 하는 일이 없음

#### 할 일
**① `SecurityConfig.java`** — `authorizeHttpRequests(auth -> auth` 바로 아래, **첫 번째 규칙으로** 한 줄 추가
```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()                  // ← 4일차: 공지 · 회원가입 · CSRF 토큰은 누구나
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()   // (여기부터 기존 코드)
```

**② `PostService.java`** — `deleteAll()` 몸통에 한 줄 추가
```java
    // URL 규칙이 없어도 메서드에서 막음 (ADMIN만)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAll() {
        posts.deleteAll(); // ← 4일차: 빠져 있던 줄. 이게 없으면 200만 돌려주고 아무것도 안 지움
    }
```

#### 확인
서버를 재시작하고 같은 명령을 다시 보냄. 회원가입도 하나 해 봄.
```powershell
curl.exe -i -X POST http://localhost:8080/api/public/signup -H "Content-Type: application/json" -d '{\"username\":\"lee\",\"password\":\"1234\"}'
```
| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/public/notices` | **200** | `[{"id":1,"title":"11월 보안 교육 안내"},{"id":2,"title":"비밀번호 변경 주기 안내"}]` |
| `POST /api/public/signup` | **201** | `{"username":"lee"}` |
| `DELETE /api/posts` (user) | 403 | `{"error":"권한이 없습니다"}` |
| `DELETE /api/posts` (admin) | 200 | `{"deleted":"all"}` |
| `GET /api/posts` | 200 | `[]` ← 진짜 지워짐 |

- [ ] 위 표대로 나옴. 확인이 끝나면 서버를 재시작해 글을 되살림

---

### 4-2. 비밀번호 변경 API (오늘의 공격 대상)

> **목적**: CSRF 공격이 노릴 "상태를 바꾸는 중요한 요청"을 만듦. 주소를 `/api/**`가 아니라 `/account/**`에 두는 이유는 1-5 (CORS 설정이 없는 서버 화면용 폼 주소라고 가정)
>
> **수정**: 가져온 `Member.java` — **메서드 한 줄 추가** / 가져온 `MemberController.java` — **메서드 하나 추가** + import 3줄 + 역할 주석 고침

#### 할 일
**① `Member.java`** — getter들 아래에 한 줄 추가하고, 역할 주석 맨 위에 4일차 줄을 더함
```java
/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더): changePassword 추가
 * ... (아래는 그대로)
 */
```
```java
    public String getRole() { return role; }                                   // (기존 코드)

    public void changePassword(String encoded) { this.password = encoded; }   // ← 4일차: 해시를 받아 바꿈 (평문을 넣지 않음)
```

**② `MemberController.java`** — 완성본 전체 (`// ← 4일차`가 바뀐 줄)
```java
package com.kyobo.lab;

import java.security.Principal;   // ← 4일차
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;   // ← 4일차
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;       // ← 4일차
import org.springframework.web.bind.annotation.RestController;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더): 비밀번호 변경 추가
 *
 * [역할] 회원 API 세 개
 *   - POST /api/public/signup   : 회원가입. 비밀번호를 해시로 바꿔서 저장함 (누구나)
 *   - GET  /api/admin/members   : DB에 저장된 회원 목록. 해시가 어떻게 생겼는지 보는 실습용 (관리자)
 *   - POST /account/password    : 로그인한 사람의 비밀번호 변경 (로그인한 사람). 오늘 CSRF 공격 대상
 *
 * [알아야 할 것]
 * - 가입할 때 역할은 요청에서 받지 않고 항상 "USER"로 정함. 요청에서 받으면 누구나 ADMIN으로 가입할 수 있음
 * - 비밀번호 변경은 "누구의 비밀번호인지"를 요청에서 받지 않고 Principal(로그인 정보)에서 꺼냄 (3일차 작성자와 같은 이유)
 * - /account/** 주소는 WebConfig의 CORS 설정(/api/**)에 안 걸림. 폼 POST는 CORS와 상관없이 서버까지 감 (가이드 1-5)
 * - 그래서 이 API를 지키는 것은 CSRF 토큰뿐임. 4-6 전까지는 공격자 사이트에서도 비밀번호가 바뀜 (4-5)
 * - 회원 목록은 실습용임. 실제 서비스는 해시도 밖으로 내보내지 않음
 */
@RestController
public class MemberController {

    // 회원가입 요청 본문 {"username":"kim","password":"1234"}을 담는 그릇
    record SignupRequest(String username, String password) {
    }

    private final MemberRepository members;
    private final PasswordEncoder encoder;

    public MemberController(MemberRepository members, PasswordEncoder encoder) {
        this.members = members;
        this.encoder = encoder;
    }

    // 회원가입. 비밀번호는 해시로 바꿔서 저장함
    @PostMapping("/api/public/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest req) {
        // 빈 아이디 · 4자 미만 비밀번호 거절 → 400
        if (req.username() == null || req.username().isBlank() || req.password() == null || req.password().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "아이디와 4자 이상 비밀번호가 필요합니다"));
        }
        // 이미 있는 아이디 → 409 Conflict
        if (members.existsByUsername(req.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "이미 있는 아이디입니다"));
        }
        // 평문이 아니라 해시를 저장함. 역할은 항상 USER
        members.save(new Member(req.username(), encoder.encode(req.password()), "USER"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", req.username())); // 201 Created
    }

    // DB에 무엇이 저장됐는지 보는 관리자용 API (실습용 — 실제 서비스는 해시도 밖으로 내보내지 않음)
    @GetMapping("/api/admin/members")
    public List<Map<String, String>> list() {
        return members.findAll().stream()
                .map(m -> Map.of("username", m.getUsername(), "role", m.getRole(), "password", m.getPassword()))
                .toList();
    }

    // ← 4일차: 비밀번호 변경. 서버 화면용 폼 주소(/account/**)라 /api/**의 CORS 설정이 적용되지 않음
    @PostMapping("/account/password")
    @Transactional // 메서드가 끝날 때 바뀐 비밀번호를 DB에 반영함 (3일차 PostService.update와 같은 방식)
    public Map<String, String> changePassword(@RequestParam String newPassword, Principal principal) { // @RequestParam: 폼 본문 newPassword=... 값
        Member m = members.findByUsername(principal.getName()).orElseThrow(); // 누구의 비밀번호인지는 로그인 정보에서
        m.changePassword(encoder.encode(newPassword));                        // 평문이 아니라 해시로 바꿈
        return Map.of("changed", principal.getName());
    }
}
```
- `@RequestParam`: JSON이 아니라 **폼 본문**(`newPassword=hacked`)에서 값을 꺼냄. 공격자 사이트의 HTML 폼이 이 모양으로 보내기 때문임
- `/account/password`는 `SecurityConfig`의 `anyRequest().authenticated()`에 걸림 → 로그인한 사람만 됨. 규칙을 새로 안 써도 됨

#### 확인
서버를 재시작하고 터미널에서 확인함.
```powershell
curl.exe -i -u user:1234 -X POST http://localhost:8080/account/password -d newPassword=5678
curl.exe -i -u user:1234 http://localhost:8080/api/me
curl.exe -i -u user:5678 http://localhost:8080/api/me
```
| 요청 | 상태 | 응답 |
|---|---|---|
| `POST /account/password` (user:1234) | 200 | `{"changed":"user"}` |
| `GET /api/me` (user:**1234**) | **401** | `{"error":"로그인이 필요합니다"}` ← 옛 비밀번호는 안 됨 |
| `GET /api/me` (user:**5678**) | 200 | `{"username":"user"}` |

- [ ] 위 표대로 나옴. 확인이 끝나면 **서버를 재시작해 비밀번호를 `1234`로 되돌림**

---

### 4-3. 저장형 XSS 공격 (코드 없음 · 일부러 뚫리는 단계)

> **목적**: "입력을 HTML로 출력하면" 무슨 일이 생기는지 봄. 공격자(kim)가 쓴 글이 **다른 사람(user)의 브라우저에서** 실행됨 (1-2)
>
> **수정**: 없음. 화면(`App.tsx`)의 `dangerouslySetInnerHTML`이 이미 구멍임 (2-3)

#### 할 일
1. 화면(`localhost:3000`)에서 `kim / 1234`로 로그인
2. 새 글 제목 칸에 아래를 그대로 붙여 넣고 **글쓰기**
   ```html
   <img src=x onerror="alert('XSS! 쿠키: ' + document.cookie)">
   ```
3. 경고창이 뜨면 **확인**을 누름. 이어서 `F5`(페이지 새로고침)도 해 봄

#### 확인 ①: 스크립트가 실행됨
| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/posts` | 200 | `[...,{"id":3,"title":"<img src=x onerror=\"alert('XSS! 쿠키: ' + document.cookie)\">","author":"kim"}]` |
| `POST /api/posts` | 201 | `{"id":3,"title":"<img src=x onerror=\"alert('XSS! 쿠키: ' + document.cookie)\">","author":"kim"}` |

- 경고창: **`XSS! 쿠키: `** ← 쿠키 부분이 **비어 있음**
- 글 목록의 3번 글 제목 칸은 **비어 보임** (깨진 그림이 들어가 있음)
- 로그아웃 → `user`로 로그인 → `F5` 해도 경고창이 뜸 → **게시판을 보는 모든 사람이 피해자**

> 서버는 제목을 **글자 그대로 저장했을 뿐**임. 화면이 그것을 HTML로 넣는 순간 `<img>`가 만들어지고, 그림(`x`)이 없어서 `onerror`가 실행됨

#### 확인 ②: 쿠키는 왜 비어 있나 (HttpOnly)
F12 → **Application** → Cookies → `http://localhost:3000` (또는 `localhost`) → `JSESSIONID` 줄의 **HttpOnly에 체크**가 있음 → 스크립트로 못 읽음

#### 확인 ③: 쿠키를 몰라도 피해는 생김
1. 서버를 재시작하고 `kim / 1234`로 로그인
2. 아래 제목으로 글쓰기 (1번 글 = user의 글을 지우는 스크립트)
   ```html
   <img src=x onerror="fetch('http://localhost:8080/api/posts/1',{method:'DELETE',credentials:'include',headers:{'X-Requested-With':'x'}})">
   ```
   - 쓰는 순간 kim의 브라우저에서도 실행되지만, kim은 1번 글의 주인이 아니라서 3일차 `PostGuard`가 막음 (403, 결과표에는 안 나옴)
3. 로그아웃 → `user / 1234`로 로그인 → **`F5`** → **새로고침** 버튼

| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/posts` | 200 | `[{"id":2,"title":"kim의 첫 글","author":"kim"},{"id":3,"title":"<img src=x onerror=\"fetch(...)\">","author":"kim"}]` ← **1번 글이 사라짐** |

- [ ] 경고창이 뜨고 쿠키 부분이 비어 있음
- [ ] user가 화면을 열기만 했는데 user의 1번 글이 지워짐

> user가 지운 것이 아님. kim의 스크립트가 **user의 세션으로** `DELETE`를 보냈고, 서버는 "user가 자기 글을 지우는 정상 요청"으로 처리함 (3일차의 소유자 검사도 통과). XSS가 생기면 **서버의 권한 검사가 모두 무력해짐**

---

### 4-4. XSS 막기 (화면 한 줄)

> **목적**: 제목을 HTML이 아니라 **글자로** 넣어서 4-3의 스크립트가 실행되지 않게 함 (1-2 이스케이프)
>
> **수정**: `day04/frontend/src/App.tsx` — 제목 칸 **한 줄**. 서버는 안 고침

#### 할 일
`App.tsx`에서 `dangerouslySetInnerHTML` 줄을 찾아 바꿈. 위의 주석 줄도 같이 고침.
```tsx
              {/* 전: 제목의 HTML을 그대로 화면에 넣음 (XSS 구멍) */}
              <td dangerouslySetInnerHTML={{ __html: p.title }} />
```
```tsx
              {/* 후: { }는 값을 글자로 넣음 (이스케이프) → <img ...>가 그림이 아니라 글자로 보임 */}
              <td>{p.title}</td>
```
- 파일을 저장하면 화면이 알아서 바뀜 (Vite가 바로 반영). 서버 재시작 필요 없음

#### 확인
화면에서 `F5`. (4-3의 XSS 글이 아직 남아 있는 상태에서 봄)

| 번호 | 제목 (화면에 보이는 그대로) | 작성자 |
|---|---|---|
| 2 | `kim의 첫 글` | kim |
| 3 | `<img src=x onerror="fetch('http://localhost:8080/api/posts/1',...)">` | kim |

- [ ] 경고창이 안 뜨고, 제목 칸에 `<img src=x ...>` **글자**가 그대로 보임
- [ ] F12 → Elements에서 그 칸(`<td>`)을 펼치면 `<img>` 태그가 없고 따옴표로 둘러싼 **글자**만 있음

> 서버는 여전히 `<img ...>`를 그대로 저장하고 돌려줌. **출력하는 쪽(화면)이 글자로 넣는 것**이 XSS 방어의 핵심임. 확인이 끝나면 서버를 재시작해 글을 정리함

---

### 4-5. CSRF 공격 (코드 없음 · 일부러 뚫리는 단계)

> **목적**: 비밀번호를 모르는 공격자 사이트가 **user의 세션 쿠키만으로** 비밀번호를 바꾸는 것을 봄 (1-3)
>
> **수정**: 없음. 지금 `SecurityConfig`는 `.csrf(csrf -> csrf.disable())`로 CSRF 보호가 꺼져 있음 (2일차에 끈 것)

#### 할 일
1. 서버를 재시작함 (비밀번호 `1234`로 시작)
2. `localhost:3000`에서 `user / 1234`로 로그인
3. **같은 브라우저의 새 탭**에서 `http://localhost:4000`을 열고 **경품 받기**를 누름

#### 확인 ①: 공격 성공
탭 주소가 `http://localhost:8080/account/password`로 바뀌고 화면에 아래가 보임
```
{"changed":"user"}
```

#### 확인 ②: 비밀번호가 진짜 바뀜
3000 화면으로 돌아가서 **로그아웃 → 로그인**을 두 번 해 봄

| 비밀번호 칸 | 요청 | 상태 |
|---|---|---|
| `1234` | `POST /api/login` | **401** |
| `hacked` | `POST /api/login` | **200** |

- [ ] user의 비밀번호가 `hacked`로 바뀜

> 공격자 사이트는 비밀번호도 쿠키 값도 몰랐음. 브라우저가 `localhost:8080`으로 가는 폼 요청에 **user의 `JSESSIONID`를 자동으로 실어 줬을 뿐**임. `frontend/evil-site/index.html`을 열어 보면 숨겨진 입력 칸 하나와 폼이 전부임
>
> CORS(`WebConfig`)는 왜 못 막았나? ① `/account/**`는 CORS 설정 대상이 아니고, ② HTML 폼 전송은 원래 다른 출처로 보낼 수 있으며, ③ 공격자는 **응답을 읽을 필요가 없었음** (1-5)

---

### 4-6. CSRF 보호 켜기

> **목적**: 2일차에 끈 CSRF 보호를 다시 켬. 켜면 공격은 막히지만 **우리 화면의 로그인까지 막히는 것**을 확인함 (1-4)
>
> **수정**: 가져온 `SecurityConfig.java` — `.csrf(csrf -> csrf.disable())` **줄과 그 위 주석 줄을 지움**

#### 할 일
`SecurityConfig`의 `filterChain()`에서 아래 두 줄을 지움.
```java
                // CSRF 보호를 끔. POST 회원가입이 403(→401)으로 막히던 원인. 4일차에 토큰 방식으로 다시 켬
                .csrf(csrf -> csrf.disable())
```
- 지우기만 하면 됨. CSRF 보호는 **스프링 시큐리티의 기본값이 "켜짐"**이라서 따로 켜는 코드가 없음

#### 확인
서버를 재시작하고 4-5를 다시 함 (3000에서 user 로그인 → 4000 경품 받기).

**① 3000에서 로그인**
| 요청 | 상태 | 응답 |
|---|---|---|
| `POST /api/login` | **403** | `{"error":"권한이 없습니다"}` ← 로그인도 막힘 |

**② 4000에서 경품 받기** (로그인이 안 됐어도 결과는 같음)
```
{"error":"권한이 없습니다"}          ← 탭 주소 http://localhost:8080/account/password, 상태 403
```

- [ ] 공격은 403으로 막힘
- [ ] 그런데 우리 화면의 로그인도 403

> CSRF 필터는 GET이 아닌 모든 요청에 토큰을 요구함. 로그인(`POST /api/login`)도 예외가 아님. 화면(`csrfHeader()`)은 `/api/public/csrf`에서 토큰을 받으려 하지만 **그 API가 아직 없어서** 토큰 없이 보냄
>
> 메시지가 "권한이 없습니다"라서 **역할 문제인지 CSRF 문제인지 구분이 안 됨.** 4-7에서 고침

---

### 4-7. 토큰 API 만들기 + 거절 이유 보이기

> **목적**: 우리 화면이 토큰을 받아 갈 API를 만들어 로그인을 되살리고, CSRF로 거절될 때는 **CSRF라고** 알려 줌
>
> **수정**: 빈 파일 `CsrfController.java` 새로 씀 + 가져온 `SecurityConfig.java` — `accessDeniedHandler` **한 줄을 두 줄로** 바꿈 + import 한 줄. 오늘 `SecurityConfig`를 고치는 단계가 하나 더(4-9) 있어서 완성본은 4-9에 둠

#### 할 일
**① `CsrfController.java`**
```java
package com.kyobo.lab;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더)
 *
 * [역할] CSRF 토큰을 내주는 API (GET /api/public/csrf). React 화면이 POST · PUT · DELETE 전에 여기서 토큰을 받아 감
 *
 * [알아야 할 것]
 * - CSRF 보호를 켜면 GET을 뺀 모든 요청(로그인 포함)에 토큰이 있어야 함. 토큰을 받는 이 API는 GET이라 토큰 없이 부를 수 있음
 * - 주소가 /api/public/** 아래라서 로그인 안 해도 부를 수 있음 (로그인 요청 전에 토큰이 먼저 필요하기 때문)
 * - 공격자 사이트도 이 주소를 부를 수는 있지만, 다른 출처라서 응답(토큰)을 읽지 못함 (CORS, 가이드 4-8)
 */
@RestController
public class CsrfController {

    // 응답: {"parameterName":"_csrf","token":"...","headerName":"X-CSRF-TOKEN"}
    @GetMapping("/api/public/csrf")
    public CsrfToken csrf(CsrfToken token) { // 파라미터에 CsrfToken을 쓰면 스프링 시큐리티가 지금 세션의 토큰을 넣어 줌
        return token;                        // 그대로 돌려주면 JSON이 됨 (headerName = 화면이 토큰을 실을 헤더 이름)
    }
}
```

**② `SecurityConfig.java`** — `exceptionHandling` 안의 `accessDeniedHandler` 줄을 바꿈
```java
import org.springframework.security.web.csrf.CsrfException;
```
```java
                        // 전
                        .accessDeniedHandler((req, res, e) -> json(res, 403, "권한이 없습니다")))         // 로그인했지만 권한 없음
```
```java
                        // 후: CSRF로 거절됐으면 그렇게 알려 줌 (역할 부족과 구분)
                        .accessDeniedHandler((req, res, e) -> json(res, 403,
                                e instanceof CsrfException ? "CSRF 토큰이 없거나 틀립니다" : "권한이 없습니다")))
```
- `CsrfException`: CSRF 필터가 토큰이 없거나 틀릴 때 던지는 예외. **이것도 `AccessDeniedException`의 한 종류**라서 3일차에 만든 같은 처리기(403)로 옴. 그래서 `instanceof`로 구분함

#### 확인
서버를 재시작함.

**① 브라우저 주소창에 `http://localhost:8080/api/public/csrf`** → `F5`를 두어 번
```
{"parameterName":"_csrf","token":"TAjLw8rmURB0o-lsmnkHkXfmbeOYpmqy...","headerName":"X-CSRF-TOKEN"}
{"parameterName":"_csrf","token":"a-SJ7zzHzt_w559mLD-Nxp_cketSch2Z...","headerName":"X-CSRF-TOKEN"}
```
- token 값이 **새로고침할 때마다 바뀜** (원본 토큰을 매번 다르게 가린 값, 1-4)

**② 3000에서 user 로그인 → 제목 `토큰 있음`으로 글쓰기**
| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/posts` | 200 | `[...,{"id":3,"title":"토큰 있음","author":"user"}]` |
| `POST /api/posts` | **201** | `{"id":3,"title":"토큰 있음","author":"user"}` |
| `POST /api/login` | **200** | (비어 있음) |

- F12 → Network → `posts`(POST) → Headers → Request Headers에 **`X-CSRF-TOKEN: ...`**이 있음. 바로 앞에 `csrf` 요청도 보임

**③ 4000에서 경품 받기**
```
{"error":"CSRF 토큰이 없거나 틀립니다"}          ← 상태 403
```

**④ 터미널 (Basic 로그인이어도 토큰이 필요함)**
```powershell
curl.exe -i -u user:1234 -X POST http://localhost:8080/account/password -d newPassword=hacked
```
```
HTTP/1.1 403
{"error":"CSRF 토큰이 없거나 틀립니다"}
```

- [ ] 로그인 200, 글쓰기 201
- [ ] 공격자 사이트와 curl은 `CSRF 토큰이 없거나 틀립니다` 403
- [ ] user로 로그아웃 → 로그인이 **`1234`로 됨** (비밀번호가 안 바뀜)

> 공격자는 쿠키는 실어 보낼 수 있지만 **토큰을 모름.** 그럼 공격자 사이트에서 `/api/public/csrf`를 부르면? → 4-8에서 확인함

---

### 4-8. CORS가 막는 것, 잘못 열었을 때

> **목적**: CORS는 "다른 출처가 **응답을 읽는 것**"을 막는다는 것을 확인함. 그리고 `*`로 넓게 열면 공격자 사이트가 **로그인한 사람의 정보와 CSRF 토큰까지** 읽어 가는 것을 봄 (1-5)
>
> **수정**: 가져온 `WebConfig.java` — `allowedOrigins` 한 줄을 **잠깐 바꿨다가 반드시 되돌림**

#### 할 일 ①: 지금 상태에서 훔쳐보기
3000에서 `user`로 로그인한 상태로 4000 탭에서 **내 정보 훔쳐보기**를 누름 (F12 → Console을 열어 둠)

#### 확인 ①
- 화면: `읽기 실패: Failed to fetch — Console 탭을 확인하세요`
- Console:
  ```
  Access to fetch at 'http://localhost:8080/api/me' from origin 'http://localhost:4000' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  ```
- 요청은 **서버까지 갔음.** 서버(스프링 CORS 설정)도 허락 안 된 출처라서 거절했고, 브라우저는 응답을 공격자 페이지에 넘겨주지 않음. 터미널로 4000인 척하면 서버의 답이 보임
  ```powershell
  curl.exe -i -u user:1234 http://localhost:8080/api/me -H "Origin: http://localhost:4000"
  ```
  ```
  HTTP/1.1 403
  Invalid CORS request
  ```
  3000인 척하면 `200`과 함께 `Access-Control-Allow-Origin: http://localhost:3000`, `Access-Control-Allow-Credentials: true` 헤더가 붙음

#### 할 일 ②: 잘못된 설정 체험 (위험! 확인 후 꼭 되돌림)
`WebConfig.java`에서 한 줄을 바꾸고 서버를 재시작함.
```java
                .allowedOriginPatterns("*")   // ← 위험! .allowedOrigins("http://localhost:3000") 대신. 모든 출처 허락
```
- `allowedOrigins("*")`는 `allowCredentials(true)`와 같이 못 씀. 서버는 뜨지만 요청마다 **500**이 나고 로그에 `When allowCredentials is true, allowedOrigins cannot contain the special value "*"`가 찍힘. `allowedOriginPatterns("*")`는 그 검사를 **피해 가는** 방법이라 더 위험함

#### 확인 ②
3000에서 다시 `user`로 로그인 → 4000 → **내 정보 훔쳐보기**
```
200 {"username":"user"}
```
- [ ] 공격자 사이트가 user의 정보를 읽음
- 같은 방법으로 공격자는 `/api/public/csrf`의 **토큰도 읽을 수 있음** (Claude Code가 확인함: `{"parameterName":"_csrf","token":"I9j9Xwt7...","headerName":"X-CSRF-TOKEN"}`). 토큰을 읽으면 4-7의 CSRF 방어도 뚫림

#### 할 일 ③: 반드시 되돌림
```java
                .allowedOrigins("http://localhost:3000")          // 이 출처에서 온 요청만 허락함
```
서버를 재시작하고 훔쳐보기가 다시 `읽기 실패`인지 확인함.

- [ ] 되돌린 뒤 `읽기 실패: Failed to fetch`

> CORS와 CSRF 토큰은 **한 팀**임. 토큰은 "공격자가 토큰을 못 읽는다"는 전제로 동작하고, 그 전제를 지켜 주는 것이 CORS(정확히는 동일 출처 정책)임. 허용 출처는 **정확한 주소로** 적음

---

### 4-9. 보안 헤더 + CSP

> **목적**: 스프링 시큐리티가 기본으로 붙이는 보안 헤더를 보고, XSS의 두 번째 방어선인 CSP를 붙임 (1-6)
>
> **수정**: 가져온 `SecurityConfig.java` — `.exceptionHandling(...)` 다음에 **`.headers(...)` 블록 추가** + 역할 주석에 4일차 줄 추가. 오늘 `SecurityConfig`를 고치는 마지막 단계라 완성본 전체도 같이 둠

#### 할 일 전에: 지금 헤더를 봄
```powershell
curl.exe -i http://localhost:8080/api/posts
```
```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
```
- 설정을 하나도 안 했는데 보안 헤더가 붙어 있음. `Strict-Transport-Security`는 없음 (HTTPS에만 붙음)

#### 할 일
**① `.headers(...)` 블록** — `.exceptionHandling(...)` 블록이 끝나는 줄 바로 아래에 추가
```java
                        .accessDeniedHandler((req, res, e) -> json(res, 403,
                                e instanceof CsrfException ? "CSRF 토큰이 없거나 틀립니다" : "권한이 없습니다")))  // (여기까지 기존 코드)
                // 기본 보안 헤더에 CSP를 더함: 같은 출처(self)의 스크립트만 실행 허락
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'")))
                .httpBasic(Customizer.withDefaults())                                                       // (여기부터 기존 코드)
```
- 바깥은 큰따옴표, 안의 `'self'`는 작은따옴표. CSP 문법에서 `'self'`는 따옴표까지 써야 함

**② 역할 주석** — 맨 위 `3일차 · 03 ...` 줄 아래에 한 줄 추가하고, `[역할]` · `[알아야 할 것]`을 아래 완성본처럼 고침
```
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더): CSRF 보호 다시 켬, CSRF 거절 메시지, CSP 헤더, /api/public/** 규칙 복구
```

**③ 완성본 전체** — 내 파일과 비교해 봄. 오늘 바꾼 곳에는 `(4-N)`을 붙여 둠. 주석 처리해 둔 옛 메모리 사용자 코드는 지워도 됨
```java
package com.kyobo.lab;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인): PasswordEncoder 추가, 메모리 사용자 삭제, CSRF 끔, 폼 로그인을 React용으로 바꿈
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나): HTTP 메서드별 규칙, 역할 계층, 메서드 보안 스위치, 401·403 JSON 응답
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더): CSRF 보호 다시 켬, CSRF 거절 메시지, CSP 헤더, /api/public/** 규칙 복구
 *
 * [역할] 보안 설정의 중심. 스프링 시큐리티에게 알려 주는 것
 *   1. 주소 + HTTP 메서드별 규칙 (문 앞 검사)                → filterChain()
 *   2. CSRF 보호 (기본으로 켜짐. 끄는 코드가 없으면 켜진 것)    → filterChain()
 *   3. 거절됐을 때 응답 모양 (401 · 403 JSON, CSRF 구분)        → filterChain()의 exceptionHandling
 *   4. 보안 헤더 (기본 헤더 + CSP)                            → filterChain()의 headers
 *   5. 로그인 방식 (HTTP Basic, 세션 로그인, 로그아웃)           → filterChain()
 *   6. 역할 계층 (ADMIN > USER), 비밀번호 비교 (BCrypt)          → roleHierarchy(), passwordEncoder()
 *   7. 메서드 보안 켜기 (@PreAuthorize 동작)                    → @EnableMethodSecurity
 *
 * [알아야 할 것]
 * - CSRF 보호가 켜져 있으면 GET이 아닌 모든 요청(로그인 · 로그아웃 포함)에 X-CSRF-TOKEN 헤더가 있어야 함
 *   → 화면은 CsrfController(/api/public/csrf)에서 토큰을 받아 감. 그래서 /api/public/** 규칙이 꼭 있어야 함
 * - CsrfException도 AccessDeniedException의 한 종류라 accessDeniedHandler로 옴 → instanceof로 메시지를 나눔
 * - CSRF 토큰이 지켜지려면 공격자가 토큰을 못 읽어야 함. 그건 WebConfig의 CORS 허용 출처가 정확해야 가능함
 * - CSP는 HTML을 내려주는 응답에서 효과가 있음. 이 서버는 JSON만 줘서 지금은 헤더가 붙는 것만 확인함
 */
@Configuration
@EnableMethodSecurity // 메서드 보안 스위치: @PreAuthorize가 붙은 빈을 대리인(프록시)으로 감싸 검사하게 함
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())                                    // WebConfig의 CORS 규칙을 보안 필터에서 씀
                // (4-6) .csrf(csrf -> csrf.disable()) 줄을 지움 → CSRF 보호가 기본으로 켜짐
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()                  // (4-1) 공지 · 회원가입 · CSRF 토큰은 누구나
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()   // 글 읽기는 누구나
                        .requestMatchers(HttpMethod.POST, "/api/posts").hasRole("USER") // 글쓰기는 ROLE_USER만 (역할 계층으로 ADMIN도)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())                                  // PUT · DELETE · /account/**는 여기 (로그인만)
                // 401 · 403을 JSON 한 모양으로 돌려줌. ExceptionTranslationFilter가 이 두 처리기를 부름
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> json(res, 401, "로그인이 필요합니다")) // 로그인 안 한 사람
                        // (4-7) CSRF로 거절됐으면 그렇게 알려 줌 (역할 부족과 구분)
                        .accessDeniedHandler((req, res, e) -> json(res, 403,
                                e instanceof CsrfException ? "CSRF 토큰이 없거나 틀립니다" : "권한이 없습니다")))
                // (4-9) 기본 보안 헤더에 CSP를 더함: 같은 출처(self)의 스크립트만 실행 허락
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'")))
                .httpBasic(Customizer.withDefaults())                               // HTTP Basic 로그인을 켬
                // 세션 로그인: POST /api/login (CSRF 토큰 필요)
                .formLogin(form -> form
                        .loginProcessingUrl("/api/login")
                        .successHandler((req, res, auth) -> res.setStatus(200))       // 성공: 페이지 이동(302) 대신 200만
                        .failureHandler((req, res, e) -> res.setStatus(401)))         // 실패: 로그인 화면 이동 대신 401만
                // 로그아웃: POST /api/logout (CSRF 토큰 필요)
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)));
        return http.build();
    }

    // 상태 코드와 {"error":"..."} 본문을 직접 써서 응답함 (3일차)
    private static void json(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8"); // charset이 없으면 한글이 깨짐
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    // 역할 계층: ADMIN은 USER가 할 수 있는 일도 모두 함 (3일차)
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER");
    }

    // 비밀번호를 BCrypt 해시로 만들고 비교함 (2일차)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```
- 옛 메모리 사용자 코드를 지우면 쓰지 않게 된 import(`User`, `UserDetailsService`, `InMemoryUserDetailsManager`)도 지움

#### 확인
서버를 재시작하고 같은 명령을 보냄.
```powershell
curl.exe -i http://localhost:8080/api/posts
```
```
HTTP/1.1 200
...
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; script-src 'self'
Content-Type: application/json
```
- [ ] `Content-Security-Policy` 줄이 추가됨
- [ ] 화면(3000)에서 로그인 · 글쓰기 · 삭제가 여전히 됨 (CSP는 8080 응답에만 붙어서 화면에는 영향 없음)

> CSP가 화면을 지키려면 **HTML을 내려주는 쪽**(지금은 Vite 3000, 운영에서는 nginx 같은 프런트 서버)에 붙어야 함 (6장 한계)

---

## 5. 막히면
| 증상 | 원인 → 해결 |
|---|---|
| `Port 8080 was already in use` | 3일차 서버가 켜져 있음. ■ 또는 `Ctrl + C` |
| 3000 화면이 하얗고 Console에 `504 (Outdated Optimize Dep)` | 공격자 사이트(4000)를 먼저 켜서 화면 서버의 준비 파일이 꼬임. 터미널 2를 `Ctrl + C` → `npm run dev` 다시 → 3000에서 `F5` |
| 4000 화면이 안 뜸 | 터미널 3을 `day04\frontend`에서 실행했는지 확인 (`npx vite evil-site --port 4000`) |
| 4-1 뒤에도 공지가 401 | 추가한 줄이 `anyRequest()` 아래에 있음. `authorizeHttpRequests` 바로 아래 첫 줄로 |
| 4-2 `/account/password`가 404 | `@PostMapping("/account/password")` 철자, 또는 서버 재시작 안 함 |
| 4-2 `/account/password`가 400 | 본문이 `newPassword=...` 폼 모양이 아님 (`-d newPassword=5678`). JSON으로 보내면 400 |
| 4-3 경고창이 안 뜸 | 이미 4-4를 했거나 제목을 잘못 붙여 넣음. 이미 있는 글은 `F5`해야 다시 실행됨 (같은 화면에서 새로고침 버튼만 누르면 안 뜰 수 있음) |
| 4-3 확인 ③에서 1번 글이 안 지워짐 | user로 로그인한 뒤 `F5`를 안 함. 새로고침 **버튼**이 아니라 브라우저 `F5`로 페이지를 다시 열어야 스크립트가 실행됨 |
| 4-5 경품 받기 뒤 `{"error":"로그인이 필요합니다"}` | 3000에서 로그인 안 함, 또는 서버 재시작으로 세션이 사라짐. 3000에서 다시 로그인 |
| 4-6 뒤 로그인이 403 | 정상임 (4-6의 확인). 4-7까지 해야 풀림 |
| 4-7 뒤에도 로그인 403 | `CsrfController` 주소 철자(`/api/public/csrf`), 또는 4-1 `/api/public/**` 규칙이 없음 → 토큰 API가 401이라 화면이 토큰을 못 받음 |
| 4-7 `cannot find symbol: class CsrfException` | import `org.springframework.security.web.csrf.CsrfException` 빠짐 |
| 4-7 뒤 curl POST가 전부 403 | 정상임. curl도 토큰이 필요함 (3-5) |
| curl `-d '{...}'`가 400 | PowerShell 5.1에서 본문에 띄어쓰기가 있으면 잘림. 띄어쓰기를 빼거나 화면에서 확인 (3-5) |
| 4-8 `allowedOrigins("*")`로 바꿨더니 모든 `/api` 요청이 500 | `allowCredentials(true)`와 함께 못 씀 (로그: `allowedOrigins cannot contain the special value "*"`). `allowedOriginPatterns("*")`로 체험 (그리고 꼭 되돌림) |
| 4-8 되돌렸는데도 훔쳐보기가 200 | 서버를 재시작 안 함 |
| 고쳤는데 결과가 그대로 | 서버를 재시작 안 함, 또는 이전 서버가 아직 떠 있음 |

---

## 6. 알려진 한계 (면접에서 물어볼 만한 부분)
1. **XSS가 있으면 CSRF 토큰도 소용없음**
   - XSS 스크립트는 **우리 화면(3000) 안에서** 돌기 때문에 CORS에 안 막히고, `/api/public/csrf`에서 토큰을 받아 그대로 실어 보낼 수 있음 → XSS를 먼저 막아야 함 (출력 이스케이프 + CSP)
2. **CSP가 지금 화면을 지키지 못함**
   - CSP는 HTML 응답에 붙어야 하는데, 오늘은 JSON을 주는 8080에 붙였음 → 운영에서는 HTML을 내려주는 프런트 서버(nginx 등)에 설정함
3. **HTTPS가 아님 (HSTS 없음)**
   - HTTP라 쿠키와 비밀번호가 평문으로 오감. 운영은 HTTPS + HSTS + 쿠키 `Secure` 표시가 기본임
4. **요청마다 토큰을 따로 받음**
   - 화면이 POST 전에 매번 `GET /api/public/csrf`를 부름 → 요청이 두 배. 실무에서는 토큰을 쿠키(`XSRF-TOKEN`)로 내려주고 화면이 읽어서 헤더에 싣는 방식(`CookieCsrfTokenRepository`)을 많이 씀
5. **비밀번호 변경에 현재 비밀번호 확인이 없음**
   - 세션만 있으면 바뀜. 중요한 작업은 **현재 비밀번호를 다시 묻는 것(재인증)**이 CSRF · 세션 탈취의 마지막 방어선임
6. **화면에서 HTML이 꼭 필요하면?**
   - 게시판에 굵게 · 링크 같은 HTML을 허락해야 한다면 `dangerouslySetInnerHTML` 전에 **허락한 태그만 남기는 정화(sanitize)** 라이브러리(예: DOMPurify)를 거쳐야 함
7. **SameSite 쿠키로는 오늘 공격을 못 막음**
   - 브라우저는 `SameSite=Lax` 기본값으로 다른 **사이트**의 POST에 쿠키를 안 보냄. 그런데 `localhost:3000`과 `localhost:4000`은 포트만 다른 **같은 사이트**라서 쿠키가 실림 → 그래서 토큰이 필요함. 실제 공격자 도메인(`evil.com`)이라면 SameSite가 보조 방어가 됨
8. **세션 + 쿠키 방식의 숙제**
   - 오늘 공격 두 개는 모두 "브라우저가 쿠키를 자동으로 보낸다"에서 시작함 → 5일차에 쿠키 · 세션 · 토큰(JWT) 방식을 비교함

---

## 7. 다 했으면
"가이드 04 다 했어"라고 알려 주세요. 이렇게 진행합니다.
1. 작성한 코드 리뷰
2. 서버 · 화면 · 공격자 사이트를 띄워 완료 기준 표대로 나오는지 함께 확인
3. 통과하면 기능 단위 커밋 (push는 직접)

## 스스로 답해볼 질문
1. 저장형 XSS에서 서버는 아무 잘못이 없어 보이는데, 진짜 원인은 어디에 있나 (면접 질문)
2. `JSESSIONID`가 HttpOnly라서 쿠키를 못 훔쳤는데, 왜 여전히 위험한가
3. CSRF 공격자 사이트는 비밀번호도 쿠키 값도 몰랐다. 그런데 어떻게 성공했나
4. CSRF 보호를 켜자 로그인까지 403이 된 이유는?
5. 공격자 사이트도 `/api/public/csrf`를 부를 수 있는데, 왜 토큰을 못 얻나
6. CORS는 요청을 막나, 응답을 막나? CORS만으로 CSRF를 못 막는 이유는?
7. `allowCredentials(true)` + `allowedOriginPatterns("*")`가 위험한 이유를 4-8 결과로 설명해 보기
8. XSS가 있는 사이트에서 CSRF 토큰이 소용없는 이유는?
9. CSP는 XSS를 어떻게 줄이나. 오늘 붙인 CSP가 화면을 못 지키는 이유는?
