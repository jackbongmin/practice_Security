# 가이드 02 · BCrypt · DB 사용자 · 세션 로그인

> **한 줄 요약**: 1일차 서버의 메모리 사용자(`{noop}1234`)를 **DB 회원 + BCrypt 해시**로 바꾸고, **회원가입 API**를 붙인 뒤, 요청마다 비밀번호를 보내는 Basic 대신 **한 번 로그인하면 세션(쿠키)으로 기억하는 로그인**을 만드는 기능 구현임

## 목표
- 비밀번호를 평문이 아니라 **BCrypt 해시**로 저장하고, 로그인할 때 어떻게 비교하는지 이해함
- 사용자를 코드(메모리)가 아니라 **DB에서 찾도록** `UserDetailsService`를 직접 구현함
- 회원가입(POST)이 막히는 원인을 **로그로 찾아봄** (CSRF)
- React 화면에서 **세션 로그인 → 쿠키(`JSESSIONID`)만으로 내가 누군지 아는 상태**를 만들고, 로그아웃과 세션 ID 교체를 확인함
- 로그인 요청이 지나가는 필터를 로그로 따라가 봄

## 완료 기준
화면(`http://localhost:3000`)에서 아래대로 나옴

**Basic 모드**
| 아이디 | 내 정보 | 관리자 통계 | 회원 목록(관리자) |
|---|---|---|---|
| user / 1234 | 200 | 403 | 403 |
| admin / 1234 | 200 | 200 | 200 (비밀번호 칸이 `$2a$10$...` 해시) |

**회원가입 · 세션 모드**
- [ ] `kim / 1234` 회원가입 → `201`, 같은 아이디로 다시 → `409`
- [ ] 회원 목록에서 **user와 kim은 둘 다 `1234`인데 해시가 서로 다름**
- [ ] 세션 모드로 로그인 `200` → 내 정보 `200` (비밀번호를 안 보냈는데 누군지 앎) → 로그아웃 `200` → 내 정보 `401`
- [ ] 서버 로그에서 `UsernamePasswordAuthenticationFilter`와 `DaoAuthenticationProvider`를 찾음

## 만들 API
| 요청 | 설명 | 누가 쓸 수 있나 |
|---|---|---|
| `GET /api/public/notices` · `GET /api/me` · `GET /api/admin/stats` | 1일차와 같음 | 1일차와 같음 |
| `POST /api/public/signup` | 회원가입 (본문 JSON) | 누구나 |
| `GET /api/admin/members` | DB에 저장된 회원 목록 (해시 확인용) | 관리자(ADMIN) |
| `POST /api/login` | 세션 로그인 (본문 `username=...&password=...`) | 누구나 |
| `POST /api/logout` | 로그아웃 (세션 지우기) | 로그인한 사람 |

응답 예시:
```json
// POST /api/public/signup  {"username":"kim","password":"1234"}
{"username": "kim"}                                  // 201 Created
{"error": "이미 있는 아이디입니다"}                     // 409 Conflict
{"error": "아이디와 4자 이상 비밀번호가 필요합니다"}       // 400 Bad Request
// GET /api/admin/members
[{"username": "user", "role": "USER", "password": "$2a$10$UsFZ..."}, ...]
// POST /api/login · POST /api/logout → 본문 없이 상태 코드만 (200 / 401)
```

---

## 1. 개념 먼저

### 1-1. 비밀번호는 "암호화"가 아니라 "해시"로 저장함
| | 암호화 | 해시 |
|---|---|---|
| 비유 | 자물쇠 상자. 열쇠가 있으면 다시 꺼낼 수 있음 | 믹서기. 갈아 버리면 원래 과일로 못 돌림 |
| 되돌리기 | 가능 (열쇠만 있으면) | **불가능** |
| 비밀번호에 | 열쇠가 털리면 전부 노출 | 이것을 씀 |

- 그럼 로그인은 어떻게 확인하나? → **입력값을 똑같이 갈아서 저장된 값과 비교함.** 원래 비밀번호를 꺼낼 필요가 없음
- 1일차의 `{noop}1234`는 평문 그대로라 DB가 털리면 비밀번호가 그대로 보임 (1일차 5장 한계 1번)

### 1-2. BCrypt와 솔트
BCrypt 해시 한 줄을 뜯어 보면:
```
$2a$10$UsFZPOgcElNqG2d74g90DO zWkvash7m7p.zUtXlUaknp1yWxIDR8G
└┬┘└┬┘└─────── 솔트 22자 ──────┘└───────── 해시 31자 ──────────┘
 │  └ 강도 10 = 2¹⁰번 반복해서 일부러 느리게 만듦 (무차별 대입을 느리게)
 └ BCrypt라는 표시
```
- **솔트(salt)**: 해시를 만들 때마다 섞는 무작위 값. 그래서 **같은 `1234`라도 해시가 매번 다름**
  - 솔트가 없으면 "`1234`의 해시는 이것" 같은 미리 계산한 표(레인보우 테이블)로 한 번에 뚫림
- 솔트는 해시 문자열 안에 들어 있음. 비교할 때 `matches(입력값, 저장된 해시)`가 솔트를 꺼내서 입력값을 똑같이 갈아 봄
  - 그래서 **해시끼리 `equals`로 비교하면 안 되고 반드시 `matches()`를 씀**

### 1-3. `PasswordEncoder` 빈 하나가 비교 방식을 정함
- `PasswordEncoder`: "비밀번호를 해시로 만들기(`encode`) / 맞는지 비교하기(`matches`)"를 맡는 도구
- `BCryptPasswordEncoder`를 빈으로 등록하면 스프링 시큐리티는 **모든 로그인 비교를 BCrypt로** 함
  - 그 순간 1일차의 `{noop}1234`(평문)는 BCrypt 모양이 아니라서 **로그인이 전부 실패함** (4-2에서 직접 봄)
  - 1일차에 `{noop}`을 붙였던 건 "기본 비교 도구"가 앞의 `{...}`를 보고 방식을 골랐기 때문임. BCrypt 빈을 직접 등록하면 그 기본 도구 대신 이것만 씀

### 1-4. 로그인이 처리되는 길: `UserDetailsService`와 `DaoAuthenticationProvider`
```
아이디·비밀번호를 꺼냄 (BasicAuthenticationFilter 또는 UsernamePasswordAuthenticationFilter)
  → DaoAuthenticationProvider (로그인 처리기)
       ① UserDetailsService.loadUserByUsername("user")  → DB에서 회원을 찾음   ← 오늘 직접 만드는 부분
       ② PasswordEncoder.matches("1234", 찾은 해시)      → 비밀번호 비교        ← 1-3의 빈
  → 성공하면 "로그인한 사람" 정보(Authentication)를 만듦
```
- `UserDetailsService`는 1일차에도 있었음(`InMemoryUserDetailsManager`). 오늘은 **같은 자리에 DB 구현(`JpaUserDetailsService`)을 꽂는 것**일 뿐임
- 스프링은 `UserDetailsService` 빈이 **딱 하나일 때만** 알아서 로그인 처리기에 꽂아 줌. 두 개면 무엇을 쓸지 몰라 **아무것도 안 꽂음** → 로그인이 전부 401 (4-4에서 직접 봄)

### 1-5. JPA · H2 · 엔티티 · 리포지토리 (DB를 처음 쓸 때 필요한 만큼만)
| 용어 | 쉬운 뜻 | 오늘 코드 |
|---|---|---|
| H2 | 서버 메모리 안에서 도는 작은 DB. **서버를 끄면 데이터가 사라짐** | `build.gradle`의 `com.h2database:h2` |
| JPA | 자바 객체를 DB 테이블에 저장·조회해 주는 도구. SQL을 직접 안 써도 됨 | `spring-boot-starter-data-jpa` |
| 엔티티 (`@Entity`) | "이 클래스 = 테이블 하나"라는 표시. 필드 = 칸 | `Member` |
| 리포지토리 | 테이블을 다루는 창구. 인터페이스만 쓰면 구현은 스프링이 만들어 줌 | `MemberRepository` |
| 메서드 이름 조회 | `findByUsername`처럼 이름만 규칙대로 지으면 SQL을 만들어 줌 | `findByUsername`, `existsByUsername` |

- DB 주소를 따로 안 적어도, 스프링 부트가 "H2가 있으니 메모리 DB를 하나 만들어 연결함"으로 알아서 처리함. 서버 시작 로그의 `url=jdbc:h2:mem:...`가 그 DB임
- 서버가 뜰 때 JPA가 `Member` 모양대로 테이블을 자동으로 만들고, `DataInitializer`가 `user`, `admin`을 넣음

### 1-6. CSRF (오늘은 "POST가 왜 막혔나"까지만)
- **CSRF(사이트 간 요청 위조)**: 내가 은행에 로그인한 채로 나쁜 사이트를 열면, 그 사이트가 몰래 은행으로 "송금" 요청을 보내는 공격. 브라우저가 은행 쿠키를 자동으로 붙여 주기 때문에 가능함
- 스프링 시큐리티는 이걸 막으려고 **POST · PUT · DELETE 요청에 "CSRF 토큰"을 요구**함. 토큰이 없으면 403
- 1일차는 GET만 써서 안 보였음 (1일차 5장 한계 6번). 오늘 회원가입(POST)에서 처음 막힘
- 오늘은 **잠시 끄고** 진행함. 왜 켜야 하는지, 켠 채로 React에서 쓰는 법은 **4일차**에 다룸

### 1-7. 세션 로그인과 쿠키
| | HTTP Basic (1일차) | 세션 로그인 (오늘) |
|---|---|---|
| 요청마다 보내는 것 | `Authorization: Basic dXNlcjoxMjM0` (아이디·비밀번호) | `Cookie: JSESSIONID=9DC7...` (열쇠 번호) |
| 서버가 기억하나 | 기억 안 함. 매번 비밀번호를 확인함 | 로그인할 때 한 번 확인하고 **세션에 "user가 로그인함"을 저장** |
| 비유 | 건물 들어갈 때마다 신분증 제시 | 처음에 신분증 보여 주고 **출입증**을 받음 |

- **세션**: 서버가 브라우저별로 들고 있는 보관함. 로그인 정보(`SecurityContext`)를 여기에 넣어 둠
- **`JSESSIONID`**: 그 보관함 번호가 담긴 쿠키. 브라우저가 다음 요청부터 자동으로 붙여 보냄
  - `HttpOnly`: 자바스크립트로 이 쿠키를 읽을 수 없게 하는 표시. XSS로 쿠키를 훔쳐 가는 것을 막음 (4일차)
- **다른 출처(3000 → 8080)에서 쿠키를 주고받으려면** 양쪽 다 허락해야 함
  - 화면: `fetch(..., { credentials: 'include' })` — "쿠키 같이 보낼게" (frontend에 이미 들어 있음)
  - 서버: `allowCredentials(true)` — "쿠키 받아도 돼"라는 응답 헤더(`Access-Control-Allow-Credentials: true`). **오늘 직접 추가함**
- **세션 고정 보호**: 로그인에 성공하면 세션 ID를 새 번호로 바꿈. 공격자가 미리 알아 둔 세션 ID를 피해자가 그대로 쓰게 만드는 공격(세션 고정)을 막음. 스프링 시큐리티 기본 동작임 (4-11에서 확인)

### 1-8. 용어 정리
| 용어 | 쉬운 뜻 |
|---|---|
| 해시 | 되돌릴 수 없게 갈아 버린 값. 같은 입력이면 같은 결과 (솔트를 빼면) |
| 솔트 | 해시할 때 섞는 무작위 값. 같은 비밀번호도 해시가 달라지게 함 |
| `encode` / `matches` | 해시 만들기 / 입력값과 해시가 맞는지 비교하기 |
| `UserDetails` | 스프링 시큐리티가 쓰는 "사용자 정보" 모양 (아이디·해시·권한) |
| `UserDetailsService` | 아이디로 `UserDetails`를 찾아 주는 창구 |
| `DaoAuthenticationProvider` | `UserDetailsService`로 찾고 `PasswordEncoder`로 비교하는 로그인 처리기 |
| `CommandLineRunner` | 서버가 다 뜬 직후 한 번 실행되는 코드 자리 |
| 생성자 주입 | 필요한 빈을 생성자 파라미터로 받는 것. 스프링이 알아서 넣어 줌 |
| `record` | 값만 담는 짧은 클래스 문법 (Java 16+). 요청 본문 JSON을 받는 데 씀 |
| `ResponseEntity` | 응답 본문과 **상태 코드(201, 409…)**를 같이 정해서 돌려주는 도구 |

---

## 2. 미리 바꿔둔 설정
- `build.gradle`: 1일차에서 직접 넣은 시큐리티 두 줄에 **DB 의존성 두 줄이 더 들어가 있음** (오늘은 고치지 않음)
  ```groovy
  implementation 'org.springframework.boot:spring-boot-starter-data-jpa' // JPA: 자바 객체 ↔ DB 테이블
  runtimeOnly 'com.h2database:h2'                                         // H2: 메모리 DB (서버 끄면 사라짐)
  ```
- `settings.gradle`: 프로젝트 이름이 `security-day2`
- `application.properties`: `spring.application.name=security-day2` 한 줄. 로그 설정은 4-7, 4-12에서 직접 추가함
- `frontend/`: 오늘은 고치지 않음. 1일차보다 버튼이 늘어남
  - **Basic / 세션** 라디오: Basic은 요청마다 `Authorization` 헤더, 세션은 `credentials: 'include'`로 쿠키를 보냄
  - **회원가입**: 입력한 아이디·비밀번호로 `POST /api/public/signup`
  - **로그인 · 로그아웃**: 세션 모드일 때만 눌림. `POST /api/login`, `POST /api/logout`
  - **회원 목록(관리자)**: `GET /api/admin/members`
  - 아이디 `user`, 비밀번호 `1234`가 미리 채워져 있음
- IntelliJ: `day02/backend/build.gradle`을 Gradle 프로젝트로 연결해야 import가 안 빨개짐 → [README "IntelliJ에서 Gradle 프로젝트 연결하기"](README.md#intellij에서-gradle-프로젝트-연결하기)

---

## 3. 실행 방법과 확인 도구
4장을 시작하기 전에 한 번 읽어 둠. 4장의 단계마다 "서버 재시작 → 화면에서 누르기 → 결과 비교"를 반복함.

### 3-1. 서버와 화면 켜기
**1일차 서버가 켜져 있으면 먼저 끔** (같은 8080 포트를 씀).
```powershell
# 터미널 1 — 서버 (저장소 루트에서)
cd day02\backend
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat bootRun
```
```powershell
# 터미널 2 — 화면 (저장소 루트에서, npm install은 처음 한 번만)
cd day02\frontend
npm install
npm run dev
```
- IntelliJ에서 실행한다면 `SecurityDay2Application`의 ▶. **재시작은 Run 창의 🔄(Rerun)**. 1일차 실행 창이 남아 있으면 ■로 끔
- 서버가 뜬 신호: `Started SecurityDay2Application`
- **`.java`나 `.properties`를 고치면 항상 서버를 재시작함.** 화면(React)은 안 건드림

| 주소 | 무엇 |
|---|---|
| `http://localhost:3000` | React 확인 화면 (거의 모든 확인) |
| `http://localhost:8080/api/me` | 주소창으로 직접 (4-11 세션 ID 비교) |

### 3-2. 화면 읽는 법
- 위쪽: 아이디 · 비밀번호 칸, **회원가입** 버튼
- 가운데: **Basic / 세션** 라디오, **로그인 · 로그아웃**(세션일 때만), API 버튼 4개, **결과 지우기**
- 아래: 결과표 `요청 | 상태 | 응답`. **최신 요청이 맨 위**에 쌓임. 단계마다 **결과 지우기**를 누르고 시작하면 보기 편함
- 4장의 "결과"는 이 결과표에 나오는 모양 그대로 적었음. 날짜(`timestamp`)와 해시 값은 매번 다름

### 3-3. 개발자 도구 (`F12`)
| 탭 | 보는 것 |
|---|---|
| **Console** | CORS 오류 (빨간 글씨) |
| **Network** | 요청 하나하나의 방식(GET · POST · OPTIONS), 상태 코드, Request Headers(`Authorization`, `Cookie`) |
| **Application → Cookies → `http://localhost:3000`** | `JSESSIONID` 값과 `HttpOnly` 체크 |

### 3-4. 화면 없이 터미널로 확인하기 (선택)
```powershell
# Basic
curl.exe -i -u user:1234 http://localhost:8080/api/me
# 회원가입 (PowerShell에서는 JSON 안의 " 를 \" 로 씀)
curl.exe -i -X POST http://localhost:8080/api/public/signup -H "Content-Type: application/json" -d '{\"username\":\"kim\",\"password\":\"1234\"}'
# 회원 목록 (해시 보기)
curl.exe -u admin:1234 http://localhost:8080/api/admin/members
# 세션 로그인 → 쿠키를 파일에 저장 → 그 쿠키로 내 정보
curl.exe -i -c cookie.txt -X POST http://localhost:8080/api/login -d "username=user&password=1234"
curl.exe -i -b cookie.txt http://localhost:8080/api/me
```
- `-c cookie.txt`: 응답의 쿠키를 저장 / `-b cookie.txt`: 저장한 쿠키를 붙여 보냄 (브라우저가 하는 일과 같음). 다 하고 `cookie.txt`는 지움

---

## 4. 단계별 진행
- 작업 폴더: `day02/backend`
- 빈 파일에는 `package` 줄, 클래스 역할 주석, 비어 있는 선언만 있음. 쓰기 전에 맨 위 주석부터 읽음
- 단계마다 **할 일 → 서버 재시작 → 결과 확인** 순서임. 결과가 다르면 다음 단계로 가지 말고 5장 "막히면"을 봄
- 코드 안의 `//` 주석은 중요한 줄의 뜻임. 따라 칠 필요는 없지만 한 번씩 읽고 넘어감

```
day02/backend/
├── build.gradle                                  (미리 바꿔 둠: JPA·H2)
└── src/main/
    ├── java/com/kyobo/lab/
    │   ├── SecurityDay2Application.java          (4-1)
    │   ├── ApiController.java                    (4-1, 1일차와 같음)
    │   ├── WebConfig.java                        (4-1 → 4-10)
    │   ├── SecurityConfig.java                   (4-1 → 4-2, 4-3, 4-5, 4-8, 4-9)
    │   ├── Member.java · MemberRepository.java   (4-1)
    │   ├── JpaUserDetailsService.java            (4-4)
    │   ├── DataInitializer.java                  (4-4)
    │   └── MemberController.java                 (4-6)
    └── resources/
        └── application.properties                (4-7, 4-12)
```

| 단계 | 할 일 | 보게 될 것 |
|---|---|---|
| 4-1 | 1일차 완성 상태 + 회원 테이블 | 1일차와 같은 결과 |
| 4-2 | `PasswordEncoder` 빈 추가 | 맞는 비밀번호도 401 (일부러) |
| 4-3 | 메모리 사용자 비밀번호를 해시로 | 다시 200 |
| 4-4 | DB 사용자 찾기 + 처음 회원 넣기 | 전부 401 (일부러) |
| 4-5 | 메모리 사용자 지우기 | DB 회원으로 로그인 |
| 4-6 | 회원가입 API | 공개 주소인데 401 (일부러) |
| 4-7 | 보안 로그 `DEBUG` | 진짜 원인: CSRF |
| 4-8 | CSRF 끄기 | 가입 201, 같은 비밀번호 다른 해시 |
| 4-9 | 폼 로그인 · 로그아웃을 React용으로 | 세션 로그인 `요청 실패` (일부러) |
| 4-10 | CORS에 쿠키 허락 | 비밀번호 없이 내 정보 200 |
| 4-11 | (코드 없음) 세션 ID 비교 | 로그인하면 세션 ID가 바뀜 |
| 4-12 | 보안 로그 `TRACE` | 로그인 요청이 지나가는 필터 |

---

### 4-1. 1일차 완성 상태 + 회원 테이블

#### 할 일
`SecurityDay2Application`은 새로 씀. `ApiController` · `WebConfig` · `SecurityConfig`는 1일차 가이드 완성본과 같음.

내 1일차 파일을 가져오려면 (저장소 루트에서, PowerShell):
```powershell
# 1일차에서 다 쳤던 세 파일을 2일차 폴더로 복사함 (빈 파일을 덮어씀)
Copy-Item day01\backend\src\main\java\com\kyobo\lab\ApiController.java, day01\backend\src\main\java\com\kyobo\lab\WebConfig.java, day01\backend\src\main\java\com\kyobo\lab\SecurityConfig.java -Destination day02\backend\src\main\java\com\kyobo\lab\ -Force
```
- 복사하면 역할 주석이 1일차 내용으로 바뀜. 동작에는 상관없지만, 2일차 빈 파일의 역할 주석(오늘 바뀌는 점이 적혀 있음)을 먼저 읽어 두고 덮어쓰면 좋음
- 1일차를 끝까지 못 했다면 복사하지 말고 아래 코드를 그대로 침

**`SecurityDay2Application.java`**
```java
package com.kyobo.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 서버 시작점. 1일차 SecurityDay1Application과 하는 일이 같고 이름만 다름
 *
 * [알아야 할 것]
 * - 같은 패키지(com.kyobo.lab)의 @Entity, @Service, @Component, @RestController, @Configuration을 찾아 등록함
 * - 2일차부터 @Entity(Member)가 생기므로 서버가 뜰 때 JPA가 H2 DB에 테이블도 만듦
 */
@SpringBootApplication // 설정 읽기 + 같은 패키지의 빈 찾기 + 자동 설정(JPA·H2 포함)을 한 번에 켬
public class SecurityDay2Application {

	public static void main(String[] args) {
		SpringApplication.run(SecurityDay2Application.class, args);
	}
}
```

**`ApiController.java`** — 코드는 1일차 가이드와 **같음** (역할 주석에 2일차 설명 한 줄만 더함)

<details>
<summary>코드 보기</summary>

```java
package com.kyobo.lab;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 *
 * [역할] 지킬 대상인 API 3개. 보호 수준이 서로 다름
 *   - GET /api/public/notices : 누구나
 *   - GET /api/me             : 로그인한 사람
 *   - GET /api/admin/stats    : 관리자(ADMIN)
 *
 * [알아야 할 것]
 * - 이 클래스에는 보안 코드가 한 줄도 없음. 막고 여는 일은 컨트롤러 앞의 보안 필터(SecurityConfig)가 함
 * - 주소를 보호 수준별로 /public, /admin 처럼 나눠 둔 덕분에 SecurityConfig에서 주소 규칙 한 줄로 묶을 수 있음
 * - 2일차에도 그대로 씀. 바뀌는 것은 "누구로 로그인했나"를 찾는 방법(DB)과 기억하는 방법(세션)뿐임
 */
@RestController         // 메서드가 돌려준 값을 JSON으로 바꿔 응답함
@RequestMapping("/api") // 이 클래스의 모든 주소 앞에 /api가 붙음
public class ApiController {

	// 누구나 봐도 되는 정보 → SecurityConfig에서 permitAll()
	@GetMapping("/public/notices")
	public List<Map<String, Object>> notices() {
		return List.of(
				Map.of("id", 1, "title", "11월 보안 교육 안내"),
				Map.of("id", 2, "title", "비밀번호 변경 주기 안내"));
	}

	// 로그인한 사용자 본인 정보 → SecurityConfig에서 authenticated()
	@GetMapping("/me")
	public Map<String, String> me(Principal principal) { // 지금 로그인한 사람. Basic이든 세션이든 스프링이 넣어 줌
		return Map.of("username", principal == null ? "(로그인 정보 없음)" : principal.getName());
	}

	// 관리자만 봐야 하는 정보 → SecurityConfig에서 hasRole("ADMIN")
	@GetMapping("/admin/stats")
	public Map<String, Object> adminStats() {
		return Map.of("members", 128, "todaySales", 3_450_000);
	}
}
```
</details>

**`WebConfig.java`** — 1일차와 같음 (4-10에서 한 줄 추가함)
```java
package com.kyobo.lab;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인): allowCredentials(true) 추가 (4-10)
 *
 * [역할] CORS 규칙. React 화면(localhost:3000)이 이 서버(localhost:8080)의 API를 부를 수 있게 허락함
 *
 * [알아야 할 것]
 * - 이 값은 SecurityConfig의 .cors(...)가 가져다 보안 필터 앞쪽에서 씀 (1일차 1-5)
 * - 세션 로그인은 쿠키(JSESSIONID)를 주고받아야 함. 다른 출처끼리 쿠키를 주고받으려면
 *   화면은 credentials: 'include', 서버는 allowCredentials(true) 둘 다 있어야 함 (가이드 1-7)
 * - allowCredentials(true)일 때는 allowedOrigins에 "*"를 쓸 수 없음. 출처를 정확히 적어야 함
 */
@Configuration // 스프링 설정 클래스라는 표시. 서버가 뜰 때 읽어 감
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")                            // /api로 시작하는 모든 주소에
				.allowedOrigins("http://localhost:3000")          // 이 출처에서 온 요청만 허락함
				.allowedMethods("GET", "POST", "PUT", "DELETE")   // 허락할 요청 방식
				.allowedHeaders("*");                             // Authorization, Content-Type 등 모든 헤더 허락
	}
}
```

**`SecurityConfig.java`** — 1일차 완성본과 같음 (오늘 여러 번 고침)
```java
package com.kyobo.lab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인): PasswordEncoder 추가, 메모리 사용자 삭제, CSRF 끔, 폼 로그인을 React용으로 바꿈
 *
 * [역할] 보안 설정의 중심. 스프링 시큐리티에게 세 가지를 알려 줌
 *   1. 주소별 규칙 (누가 어디에 들어갈 수 있나)                  → filterChain()
 *   2. 로그인 방식 (HTTP Basic, 세션 로그인 /api/login, 로그아웃)   → filterChain()
 *   3. 비밀번호 비교 방식 (BCrypt)                               → passwordEncoder()
 *
 * [알아야 할 것]
 * - 2일차 끝에는 사용자 목록이 이 클래스에 없음. JpaUserDetailsService(@Service)가 DB에서 찾음
 * - UserDetailsService 빈이 두 개면 스프링이 둘 다 안 씀 → 메모리 사용자 메서드는 반드시 지움 (4-4)
 * - CSRF를 잠시 끔. POST 회원가입이 막히던 원인. 4일차에 토큰 방식으로 다시 켬
 * - 규칙은 위에서부터 처음 맞는 하나만 씀. anyRequest()는 항상 맨 마지막 (1일차와 같음)
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/public/**").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.formLogin(Customizer.withDefaults());
		return http.build();
	}

	// 1일차 완성 상태: 메모리 사용자 2명, 비밀번호는 평문({noop}). 4-3에서 해시로, 4-5에서 통째로 지움
	@Bean
	public UserDetailsService userDetailsService() {
		return new InMemoryUserDetailsManager(
				User.withUsername("user").password("{noop}1234").roles("USER").build(),
				User.withUsername("admin").password("{noop}1234").roles("ADMIN").build());
	}
}
```
- 역할 주석의 `2일차 · 02: ...` 줄처럼, **이전 일차에 만든 클래스를 고치면 그 일차 줄을 한 줄 더함** (README 주석 규칙)

**`Member.java`** (회원 테이블)
```java
package com.kyobo.lab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 회원 테이블(MEMBER). 객체 하나 = 회원 한 명 = 테이블 한 줄. 아이디 · 비밀번호 해시 · 역할을 저장함
 *
 * [알아야 할 것]
 * - @Entity를 붙이면 서버가 뜰 때 JPA가 이 모양대로 H2 DB에 테이블을 만듦 (서버를 끄면 사라짐)
 * - password 칸에는 평문이 아니라 BCrypt 해시($2a$10$...)가 들어감. 평문을 넣는 코드는 어디에도 없어야 함
 * - role에는 "USER" 또는 "ADMIN"만 넣음. ROLE_ 접두사는 로그인할 때 roles(...)가 붙여 줌
 * - 파라미터 없는 생성자(protected)는 JPA가 DB에서 꺼내 객체를 만들 때 씀. 지우면 오류 남
 */
@Entity // 이 클래스 = 테이블 하나. 필드 하나 = 칸 하나
public class Member {

	@Id             // 기본 키(줄마다 다른 번호)
	@GeneratedValue // 번호는 DB가 자동으로 매김
	private Long id;

	@Column(unique = true, nullable = false) // 같은 아이디 두 번 저장 불가, 빈 값 불가
	private String username;

	@Column(nullable = false)
	private String password; // BCrypt 해시

	@Column(nullable = false)
	private String role; // USER 또는 ADMIN

	protected Member() { // JPA 전용. 직접 쓰지 않음
	}

	public Member(String username, String password, String role) {
		this.username = username;
		this.password = password;
		this.role = role;
	}

	public Long getId() { return id; }
	public String getUsername() { return username; }
	public String getPassword() { return password; }
	public String getRole() { return role; }
}
```

**`MemberRepository.java`**
```java
package com.kyobo.lab;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 회원 테이블을 다루는 창구. 저장(save) · 전체 조회(findAll) · 아이디로 찾기를 함
 *
 * [알아야 할 것]
 * - 인터페이스만 쓰면 구현 클래스는 스프링이 서버 시작 때 자동으로 만들어 빈으로 등록함
 * - JpaRepository<Member, Long>: Member 테이블을, 기본 키 타입 Long으로 다룸. save · findAll · findById는 이미 들어 있음
 * - findByUsername처럼 이름을 규칙대로 지으면 "username 칸이 같은 줄을 찾아라" SQL을 알아서 만듦
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

	// 로그인할 때 씀 (JpaUserDetailsService). 없으면 빈 Optional
	Optional<Member> findByUsername(String username);

	// 회원가입할 때 아이디 중복 확인에 씀 (MemberController)
	boolean existsByUsername(String username);
}
```
- `Optional<Member>`: "있을 수도, 없을 수도 있음"을 나타내는 상자. 없을 때 `null` 대신 빈 상자를 돌려줌
- `Member`, `MemberRepository`는 아직 아무도 쓰지 않음. 4-4부터 씀

#### 확인
서버를 시작하고, 화면에서 **Basic** 상태 그대로(`user / 1234`) 버튼 3개를 누름.

| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/public/notices` | 200 | `[{"id":1,"title":"11월 보안 교육 안내"},{"id":2,"title":"비밀번호 변경 주기 안내"}]` |
| `GET /api/me` | 200 | `{"username":"user"}` |
| `GET /api/admin/stats` | **403** | `{"timestamp":"...","status":403,"error":"Forbidden","path":"/api/admin/stats"}` |

- [ ] 1일차 결과와 같음
- [ ] 서버 시작 로그에 오류가 없음. `Ctrl + F`로 `jdbc:h2`를 찾으면 `url=jdbc:h2:mem:...` 줄이 보임 (메모리 DB가 만들어짐, 1-5)

---

### 4-2. `PasswordEncoder` 빈 추가 (일부러 로그인이 깨지는 단계)

#### 할 일
`SecurityConfig` 클래스 맨 아래(`userDetailsService()` 아래)에 메서드를 추가하고, import 두 줄을 추가함.
```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
```
```java
	// 비밀번호를 BCrypt 해시로 만들고(encode) 비교함(matches). 이 빈이 생기면 모든 로그인 비교가 BCrypt로 바뀜
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(); // 기본 강도 10 (2¹⁰번 반복)
	}
```

#### 확인
서버를 재시작하고, `user / 1234`로 내 정보를 누름.

| 요청 | 상태 | 응답 |
|---|---|---|
| `GET /api/me` | **401** | (비어 있음) |

서버 콘솔:
```
WARN ... o.s.s.c.bcrypt.BCryptPasswordEncoder     : Encoded password does not look like BCrypt
```
- [ ] 비밀번호 `1234`가 맞는데도 401
- [ ] 콘솔에 위 경고가 찍힘

> 저장된 비밀번호는 아직 `{noop}1234`(평문)인데, 이제 BCrypt로만 비교하니 맞을 수가 없음 (1-3). "BCrypt 모양이 아니다"라는 경고가 그 뜻임

---

### 4-3. 메모리 사용자 비밀번호를 해시로

#### 할 일
`SecurityConfig`의 `userDetailsService()` 메서드를 아래로 바꿈.
```java
	// 메모리 사용자 2명. 비밀번호를 평문 대신 해시로 넣음 (4-5에서 통째로 지움)
	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder encoder) { // 4-2의 빈을 스프링이 넣어 줌
		return new InMemoryUserDetailsManager(
				// encoder.encode("1234") → "$2a$10$..." 해시가 저장됨. 서버를 켤 때마다 다른 값
				User.withUsername("user").password(encoder.encode("1234")).roles("USER").build(),
				User.withUsername("admin").password(encoder.encode("1234")).roles("ADMIN").build());
	}
```
- 화면은 여전히 `1234`를 보내지만 서버는 **해시끼리가 아니라 `matches("1234", 저장된 해시)`로** 비교함 (1-2)
- `@Bean` 메서드에 파라미터를 쓰면, 그 타입의 빈을 스프링이 찾아서 넣어 줌

#### 확인
서버를 재시작하고 누름.

| 로그인 | 요청 | 상태 | 응답 |
|---|---|---|---|
| user / 1234 | `GET /api/me` | 200 | `{"username":"user"}` |
| user / 1234 | `GET /api/admin/stats` | 403 | `{"timestamp":"...","status":403,"error":"Forbidden",...}` |
| admin / 1234 | `GET /api/admin/stats` | 200 | `{"todaySales":3450000,"members":128}` |

- [ ] 다시 로그인이 됨. 콘솔의 `does not look like BCrypt` 경고가 없음
- 응답 JSON 안의 순서(`todaySales`, `members`)는 바뀌어 나올 수 있음. `Map.of`는 순서를 정하지 않음

---

### 4-4. DB에서 사용자 찾기 + 처음 회원 넣기 (일부러 로그인이 깨지는 단계)

#### 할 일
**`JpaUserDetailsService.java`**
```java
package com.kyobo.lab;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 로그인할 때 스프링 시큐리티가 "이 아이디의 사용자 정보 줘"라고 부르는 창구의 DB 구현
 *   → 1일차 InMemoryUserDetailsManager(메모리 사용자) 자리를 대신함
 *
 * [알아야 할 것]
 * - @Service로 빈이 되면 스프링이 알아서 로그인 처리기(DaoAuthenticationProvider)에 꽂음. 직접 부르는 곳은 없음
 * - 단, UserDetailsService 빈이 이것 하나뿐이어야 함. SecurityConfig에 메모리 사용자가 남아 있으면 둘 다 안 씀 (4-4)
 * - 비밀번호 비교는 여기서 하지 않음. DB의 해시를 그대로 넘기면 PasswordEncoder.matches()가 비교함
 * - 없는 아이디면 UsernameNotFoundException을 던짐 → 스프링이 401로 바꿈
 */
@Service // 이 클래스를 빈으로 등록함 (@Component와 같은데 "업무 로직"이라는 표시)
public class JpaUserDetailsService implements UserDetailsService {

	private final MemberRepository members;

	public JpaUserDetailsService(MemberRepository members) { // 생성자 주입: 스프링이 MemberRepository 빈을 넣어 줌
		this.members = members;
	}

	// 로그인할 때마다 스프링 시큐리티가 부름. 입력한 아이디가 username으로 들어옴
	@Override
	public UserDetails loadUserByUsername(String username) {
		Member m = members.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException(username)); // 없는 아이디 → 로그인 실패(401)
		// DB의 Member를 스프링 시큐리티가 아는 모양(UserDetails)으로 바꿔서 돌려줌
		return User.withUsername(m.getUsername())
				.password(m.getPassword()) // DB의 해시 그대로. 비교는 PasswordEncoder가 함
				.roles(m.getRole())        // "USER" → 내부에는 ROLE_USER
				.build();
	}
}
```

**`DataInitializer.java`**
```java
package com.kyobo.lab;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 서버가 뜰 때 DB에 처음 회원 두 명(user, admin)을 넣음
 *
 * [알아야 할 것]
 * - CommandLineRunner의 run()은 서버가 다 뜬 직후 한 번 실행됨
 * - H2 메모리 DB라 서버를 재시작하면 DB가 비고, 다시 이 두 명만 들어감 (회원가입한 사람은 사라짐)
 * - 비밀번호는 반드시 encoder.encode()로 해시해서 넣음. 평문을 넣으면 BCrypt 비교에서 로그인이 안 됨
 */
@Component // 빈으로 등록함. 그래야 스프링이 run()을 불러 줌
public class DataInitializer implements CommandLineRunner {

	private final MemberRepository members;
	private final PasswordEncoder encoder; // 4-2에서 만든 BCrypt 빈

	public DataInitializer(MemberRepository members, PasswordEncoder encoder) {
		this.members = members;
		this.encoder = encoder;
	}

	@Override
	public void run(String... args) {
		members.save(new Member("user", encoder.encode("1234"), "USER"));
		members.save(new Member("admin", encoder.encode("1234"), "ADMIN"));
	}
}
```
- `SecurityConfig`의 메모리 사용자는 **아직 지우지 않음.** 메모리 사용자 + DB 사용자 둘 다 있는 상태로 일부러 서버를 켜 봄

#### 확인
서버를 재시작하고 누름.

| 로그인 | 요청 | 상태 | 응답 |
|---|---|---|---|
| user / 1234 | `GET /api/me` | **401** | (비어 있음) |
| admin / 1234 | `GET /api/me` | **401** | (비어 있음) |

서버 시작 로그 (WARN):
```
Found 2 UserDetailsService beans, with names [jpaUserDetailsService, userDetailsService].
Global Authentication Manager will not use a UserDetailsService for username/password login.
```
- [ ] 누구로 해도 401
- [ ] 시작 로그에 위 경고가 있음 (`Ctrl + F`로 `Found 2` 검색)

> 메모리 사용자(`userDetailsService`)와 DB 사용자(`jpaUserDetailsService`) 중 무엇을 쓸지 몰라서, 스프링이 **둘 다 로그인 처리기에 안 꽂음** (1-4). 에러로 멈추지 않고 경고만 찍기 때문에 놓치기 쉬움

---

### 4-5. 메모리 사용자 지우기

#### 할 일
- `SecurityConfig`의 `userDetailsService(...)` 메서드를 **통째로 지움** (위의 `// 메모리 사용자 2명` 주석까지)
- 쓰지 않게 된 import 세 줄도 지움: `User`, `UserDetailsService`, `InMemoryUserDetailsManager`
- 이제 `UserDetailsService` 빈은 `JpaUserDetailsService` 하나뿐임

#### 확인
서버를 재시작하고 누름.

| 로그인 | 요청 | 상태 | 응답 |
|---|---|---|---|
| user / 1234 | `GET /api/me` | 200 | `{"username":"user"}` |
| user / 1234 | `GET /api/admin/stats` | 403 | `{"timestamp":"...","status":403,"error":"Forbidden",...}` |
| admin / 1234 | `GET /api/admin/stats` | 200 | `{"todaySales":3450000,"members":128}` |
| user / 9999 | `GET /api/me` | 401 | (비어 있음) |
| nobody / 1234 | `GET /api/me` | 401 | (비어 있음) |

- [ ] 위 표대로 나옴. 이제 **DB 회원으로** 로그인함
- [ ] 시작 로그에 `Found 2 UserDetailsService beans`가 **더 이상 없음**

---

### 4-6. 회원가입 API (일부러 막히는 단계)

#### 할 일
**`MemberController.java`**
```java
package com.kyobo.lab;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 회원 API 두 개
 *   - POST /api/public/signup : 회원가입. 비밀번호를 해시로 바꿔서 저장함 (누구나)
 *   - GET  /api/admin/members : DB에 저장된 회원 목록. 해시가 어떻게 생겼는지 보는 실습용 (관리자)
 *
 * [알아야 할 것]
 * - 주소를 /api/public/**, /api/admin/** 밑에 둔 덕분에 SecurityConfig 규칙을 새로 안 써도 됨
 * - 그런데도 회원가입은 처음에 막힘. 원인은 4-6, 4-7에서 로그로 찾음 (CSRF)
 * - 가입할 때 역할은 요청에서 받지 않고 항상 "USER"로 정함. 요청에서 받으면 누구나 ADMIN으로 가입할 수 있음
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
	public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest req) { // @RequestBody: 본문 JSON → SignupRequest
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
}
```
- `record SignupRequest(...)`: 값만 담는 짧은 클래스. `req.username()`처럼 꺼냄
- `ResponseEntity`: 본문과 **상태 코드**를 같이 정함. `badRequest()` = 400, `CONFLICT` = 409, `CREATED` = 201

#### 확인
서버를 재시작하고, 아이디 `kim`, 비밀번호 `1234`를 넣고 **회원가입**. 그다음 `admin / 1234`로 바꿔 **회원 목록(관리자)**.

| 요청 | 상태 | 응답 |
|---|---|---|
| `POST /api/public/signup` (kim) | **401** | (비어 있음) |
| `GET /api/admin/members` (admin) | 200 | `[{"role":"USER","password":"$2a$10$...","username":"user"},{"role":"ADMIN","password":"$2a$10$...","username":"admin"}]` |

- [ ] 회원가입이 **401** ← 공개 경로(`/api/public/**`)인데?
- [ ] 회원 목록에 **kim이 없음** (가입이 안 됨)

> 원인을 아직 모름. 다음 단계에서 로그로 찾음

---

### 4-7. 보안 로그 `DEBUG`로 원인 찾기

#### 할 일
`application.properties`를 아래로 바꿈.
```properties
spring.application.name=security-day2

# 회원가입이 왜 막혔는지 보려고 보안 로그를 켬 (DEBUG: 요청마다 결정만 찍음. TRACE보다 덜 자세함)
logging.level.org.springframework.security=DEBUG
```

#### 확인
서버를 재시작하고 `kim / 1234` 회원가입을 한 번 더 누름. 서버 콘솔에서 `Securing POST /api/public/signup`을 찾음.

결과표는 4-6과 같음 (`401`, 비어 있음). 서버 콘솔:
```
Securing POST /api/public/signup
Invalid CSRF token found for http://localhost:8080/api/public/signup
Responding with 403 status code
Securing POST /error
```
- [ ] `Invalid CSRF token found` 줄이 보임
- [ ] 그 바로 뒤에 `Securing POST /error`가 이어짐

> 진짜 원인은 **CSRF 보호(403)**임 (1-6). 거절된 요청이 에러 응답을 만들려고 `/error`로 다시 보내졌는데, **`/error`는 규칙상 로그인이 필요**해서(`anyRequest().authenticated()`) 최종 응답이 401로 바뀌었음. 상태 코드만 보면 원인을 잘못 짚기 쉬워서 로그를 봐야 함

---

### 4-8. CSRF 끄기

#### 할 일
`SecurityConfig`의 `.cors(...)` 바로 아래에 한 줄을 추가함.
```java
				.cors(Customizer.withDefaults())
				// CSRF 보호를 끔. POST 회원가입이 403(→401)으로 막히던 원인. 4일차에 토큰 방식으로 다시 켬
				.csrf(csrf -> csrf.disable())
```

#### 확인
서버를 재시작하고 차례로 누름.

| 순서 | 아이디 / 비밀번호 | 버튼 | 상태 | 응답 |
|---|---|---|---|---|
| ① | kim / 1234 | 회원가입 | **201** | `{"username":"kim"}` |
| ② | kim / 1234 | 회원가입 (한 번 더) | 409 | `{"error":"이미 있는 아이디입니다"}` |
| ③ | lee / 12 | 회원가입 | 400 | `{"error":"아이디와 4자 이상 비밀번호가 필요합니다"}` |
| ④ | kim / 1234 | 내 정보 | 200 | `{"username":"kim"}` |
| ⑤ | admin / 1234 | 회원 목록(관리자) | 200 | 아래 |

⑤의 응답 (해시 값은 매번 다름):
```json
[{"role":"USER","password":"$2a$10$UsFZPOgcElNqG2d74g90DOzWkvash7m7p.zUtXlUaknp1yWxIDR8G","username":"user"},
 {"role":"ADMIN","password":"$2a$10$.tvXqKGj4lLtZzvOl31WIu3R0cfbBV9qL44AbloLfjPwnGshXz4Ku","username":"admin"},
 {"role":"USER","password":"$2a$10$Xsi.XK9uhHGWUIkw2v2e4OH798fg7IDNs7Wayoeqd6n93UXsuhTsa","username":"kim"}]
```
- [ ] 비밀번호 칸이 모두 `$2a$10$...`
- [ ] **user와 kim은 둘 다 `1234`인데 해시가 다름** (솔트, 1-2)
- 콘솔의 `Invalid CSRF token` 줄이 더 이상 안 나옴

> 서버를 재시작하면 kim이 사라짐. H2 메모리 DB라 정상임

---

### 4-9. 폼 로그인 · 로그아웃을 React용으로 (일부러 실패하는 단계)

#### 할 일
`SecurityConfig`의 `.formLogin(Customizer.withDefaults());`를 아래로 바꿈. (`;`는 맨 끝 `.logout(...)` 뒤로 옮겨 감)
```java
				.httpBasic(Customizer.withDefaults())
				// 세션 로그인: POST /api/login으로 아이디·비밀번호를 받아 확인하고, 성공하면 로그인 상태를 세션에 저장함
				.formLogin(form -> form
						.loginProcessingUrl("/api/login")                             // 로그인 요청을 받을 주소 (기본은 /login)
						.successHandler((req, res, auth) -> res.setStatus(200))       // 성공: 페이지 이동(302) 대신 200만
						.failureHandler((req, res, e) -> res.setStatus(401)))         // 실패: 로그인 화면 이동 대신 401만
				// 로그아웃: POST /api/logout으로 세션을 없앰
				.logout(logout -> logout
						.logoutUrl("/api/logout")
						.logoutSuccessHandler((req, res, auth) -> res.setStatus(200)));
```
- 기본 폼 로그인은 성공하면 다른 페이지로 **이동(302)**시킴. 화면 이동은 React가 할 일이라 서버는 **상태 코드만** 돌려줌
- `(req, res, auth) -> ...`: 성공했을 때 실행할 코드를 람다로 넘김. `res.setStatus(200)` = 본문 없이 200
- 아이디·비밀번호는 요청 본문 `username=user&password=1234`(폼 형식)로 받음. 칸 이름 `username`, `password`가 기본값임

#### 확인
서버를 재시작하고, 라디오를 **세션**으로 바꾸고 `user / 1234`로 **로그인**.

| 요청 | 상태 | 응답 |
|---|---|---|
| `POST /api/login` | **요청 실패** | 네트워크 오류 또는 CORS 차단 — 브라우저 개발자 도구 Console 탭을 확인하세요 |

F12 → Console (빨간 글씨, 줄임):
```
Access to fetch at 'http://localhost:8080/api/login' from origin 'http://localhost:3000' has been blocked by CORS policy:
Response to preflight request doesn't pass access control check: The value of the 'Access-Control-Allow-Credentials'
header in the response is '' which must be 'true' when the request's credentials mode is 'include'.
```
- [ ] Console의 CORS 오류에 `Access-Control-Allow-Credentials`가 들어 있음
- [ ] F12 → Network에서 `login` 요청이 `CORS error`로 보임

> 세션 모드에서 화면은 쿠키를 주고받으려고 `credentials: 'include'`로 요청함. 사전 요청(OPTIONS)의 응답에 "쿠키 받아도 됨"(`Access-Control-Allow-Credentials: true`)이 없어서 브라우저가 **진짜 로그인 요청(POST)을 아예 보내지 않았음** (1-7)

---

### 4-10. CORS에 쿠키 허락

#### 할 일
`WebConfig`의 `.allowedHeaders("*")` 뒤에 한 줄을 추가함. **앞 줄 끝의 `;`를 지움.**
```java
				.allowedHeaders("*")                              // Authorization, Content-Type 등 모든 헤더 허락
				.allowCredentials(true);                          // 쿠키(JSESSIONID)를 주고받는 요청도 허락함 (세션 로그인에 필요)
```

#### 확인
서버를 재시작하고, **세션** 모드 그대로 차례로 누름.

| 순서 | 버튼 | 상태 | 응답 |
|---|---|---|---|
| ① | 로그인 (user / 1234) | 200 | (비어 있음) |
| ② | 내 정보 | 200 | `{"username":"user"}` ← **비밀번호를 안 보냈는데 누군지 앎** |
| ③ | 관리자 통계 | 403 | `{"timestamp":"...","status":403,"error":"Forbidden",...}` |
| ④ | 로그아웃 | 200 | (비어 있음) |
| ⑤ | 내 정보 | **401** | (비어 있음) ← 쿠키는 남아 있어도 서버 쪽 세션이 없어짐 |
| ⑥ | 로그인 (user / 9999) | 401 | (비어 있음) |

①과 ② 사이에 F12로 확인:
- [ ] Application → Cookies → `http://localhost:3000`에 `JSESSIONID`가 있고 **HttpOnly**에 체크
- [ ] Network → 내 정보 요청 → Request Headers에 `Cookie: JSESSIONID=...`가 있고 **`Authorization`은 없음**

> ③: 세션으로 로그인해도 역할 검사는 그대로임. 로그인 방법이 바뀌었을 뿐 주소 규칙은 같음

---

### 4-11. 로그인하면 세션 ID가 바뀜 (코드 없음)

#### 할 일
1. 세션 모드에서 로그아웃 상태로 만듦
2. 새 탭 주소창에 `http://localhost:8080/api/me` → 로그인 화면(Please sign in)으로 이동함. **이때 로그인 전 세션이 만들어짐**
3. React 탭 F12 → Application → Cookies에서 `JSESSIONID` 값을 메모함 (값 A)
   - 같은 `localhost` 쿠키라 8080 탭에서 생긴 쿠키가 3000 탭에서도 보임
4. React에서 세션 모드로 로그인한 뒤 Cookies를 새로고침함 (값 B)

#### 확인
| | 값 (예시) |
|---|---|
| A (로그인 전) | `9DC74381A2B1...` |
| B (로그인 후) | `1F0E6C27D94A...` |

- [ ] A와 B가 **다름** (세션 고정 보호, 1-7)

---

### 4-12. 보안 로그 `TRACE`로 로그인 요청 따라가기

#### 할 일
`application.properties`의 `DEBUG`를 `TRACE`로 바꿈.
```properties
spring.application.name=security-day2

# 로그인 요청이 어떤 필터를 거치는지 로그로 봄 (TRACE: 필터 하나하나를 지날 때마다 찍음)
logging.level.org.springframework.security=TRACE
```

#### 확인
서버를 재시작함.

**① 시작 로그** — `Will secure any request with filters:` 줄 (한 줄로 길게 나옴):
```
Will secure any request with filters: DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter,
SecurityContextHolderFilter, HeaderWriterFilter, CorsFilter, LogoutFilter, UsernamePasswordAuthenticationFilter,
DefaultLoginPageGeneratingFilter, DefaultLogoutPageGeneratingFilter, BasicAuthenticationFilter,
RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter,
ExceptionTranslationFilter, AuthorizationFilter
```
- [ ] 1일차 16개에서 **15개**. `CsrfFilter`가 빠졌음 (4-8에서 껐기 때문)

**② 세션 모드로 로그인** → `Securing POST /api/login` 아래:
```
Invoking UsernamePasswordAuthenticationFilter (7/15)
Authenticating request with DaoAuthenticationProvider (1/1)
Authenticated user
Changed session id from 9DC74381...          ← 로그인 전 세션이 있었을 때만 나옴 (4-11)
Stored SecurityContextImpl [Authentication=UsernamePasswordAuthenticationToken ...
```

**③ 이어서 내 정보** → `Securing GET /api/me` 아래:
```
Retrieved SecurityContextImpl [Authentication=UsernamePasswordAuthenticationToken ...
```

**④ 표를 채움**

| 질문 | 내 답 |
|---|---|
| 아이디·비밀번호를 꺼낸 필터는? 몇 번째인가? | |
| DB에서 사용자를 찾고 비밀번호를 비교한 것은? | |
| 로그인 요청은 AuthorizationFilter(15/15)까지 가는가? | |
| 다음 요청에서 "누구인지"는 어디서 꺼내 오는가? | |

<details>
<summary>정답 보기</summary>

1. `UsernamePasswordAuthenticationFilter` (7/15) — `POST /api/login`일 때만 동작함
2. `DaoAuthenticationProvider` — 우리가 만든 `JpaUserDetailsService`로 사용자를 찾고 `PasswordEncoder.matches()`로 비교함 (1-4)
3. 가지 않음 — 7번에서 로그인을 처리하고 바로 응답(200)함. 컨트롤러도 없음
4. 세션 — 로그인 때 `Stored SecurityContextImpl`로 세션에 저장한 것을 `SecurityContextHolderFilter`(3/15)가 `Retrieved`로 꺼냄

</details>

- 로그가 너무 많으면 확인 후 `TRACE`를 `DEBUG`로 바꿈. IntelliJ 실행 창에서 `Ctrl + F`로 검색

---

## 5. 막히면
| 증상 | 원인 → 해결 |
|---|---|
| `Port 8080 was already in use` | 1일차 서버가 켜져 있음. 1일차 실행 창 ■ 또는 터미널 `Ctrl + C` |
| import가 빨갛게 보임 (`jakarta.persistence` 등) | `day02/backend`를 Gradle 프로젝트로 연결 안 함 → README "IntelliJ에서 Gradle 프로젝트 연결하기" |
| `No qualifying bean of type 'PasswordEncoder'` | 4-2의 `passwordEncoder()` 빈이 없음 |
| 모든 로그인이 401 + 시작 로그 `Found 2 UserDetailsService beans` | 4-5를 안 함. 메모리 사용자 메서드를 지움 |
| 모든 로그인이 401 + `Encoded password does not look like BCrypt` | 비밀번호를 `encoder.encode(...)` 없이 평문으로 넣음 |
| `No default constructor for entity` | `Member`의 `protected Member() {}`를 지움 |
| 회원가입이 401 | 4-8(CSRF 끄기)을 안 함. 4-7 참고 |
| 회원가입이 400 | 아이디가 비었거나 비밀번호가 4자 미만 |
| 서버를 재시작하니 kim이 없음 | H2 메모리 DB라 정상. 다시 가입함 |
| 로그인 버튼이 안 눌림 | 라디오를 **세션**으로 바꿈 |
| 세션 모드 로그인이 `요청 실패` | 4-10 `allowCredentials(true)`가 없음 (앞 줄 `;` 확인) |
| 세션 로그인 `200`인데 내 정보 `401` | 서버를 재시작해서 세션이 사라짐. 다시 로그인함 |
| 고쳤는데 결과가 그대로 | 서버를 재시작 안 함, 또는 이전 서버가 아직 떠 있음 (`Port 8080` 오류가 Run 창에 있는지 확인) |

---

## 6. 알려진 한계 (면접에서 물어볼 만한 부분)
1. **CSRF 보호를 꺼 둠**
   - 세션(쿠키) 로그인은 CSRF 공격 대상이 됨. 지금 서버는 다른 사이트가 로그인한 사용자 몰래 POST를 보내도 막지 못함 → 4일차에 토큰 방식으로 다시 켬
2. **회원 목록 API가 비밀번호 해시를 밖으로 내보냄**
   - 해시라도 유출되면 오프라인에서 무차별 대입을 시도할 수 있음. 실습용임 → 실제 서비스는 응답용 객체(DTO)를 따로 만들어 해시를 빼고 보냄
3. **H2 메모리 DB라 서버를 끄면 회원이 사라짐**
   - 실제 서비스는 MySQL 같은 파일·서버 DB를 씀. 코드(JPA)는 거의 그대로 쓸 수 있음
4. **회원가입 검사가 약함**
   - 비밀번호 4자 이상만 봄. 실제는 길이·조합 규칙, 이메일 인증, 같은 IP의 대량 가입 제한 등이 필요함
5. **로그인 실패 횟수 제한이 없음**
   - 비밀번호를 무한히 대입해 볼 수 있음 (BCrypt가 느리게 만들어 주지만 막지는 못함) → 계정 잠금, 캡차 등이 필요함
6. **세션이 서버 메모리에 있음**
   - 서버를 여러 대로 늘리면 A 서버에서 로그인한 세션을 B 서버가 모름 → 5일차에 토큰(JWT) 방식과 비교함
7. **권한 검사가 아직 주소 단위뿐임**
   - "내가 쓴 글만 수정" 같은 규칙은 못 정함 → 3일차

---

## 7. 다 했으면
"가이드 02 다 했어"라고 알려 주세요. 이렇게 진행합니다.
1. 작성한 코드 리뷰
2. 서버를 띄워 완료 기준대로 나오는지 함께 확인
3. 통과하면 기능 단위 커밋 (push는 직접)
4. 3일차(`day03`)는 오늘 완성한 코드를 그대로 이어받아 시작함

## 스스로 답해볼 질문
1. 비밀번호를 암호화가 아니라 해시로 저장하는 이유는? 해시로 저장하면 로그인은 어떻게 확인하나 (면접 질문)
2. 같은 `1234`인데 user와 kim의 해시가 다른 이유는? 그런데도 `matches()`가 맞다고 판단하는 이유는?
3. `PasswordEncoder` 빈만 추가했는데 왜 로그인이 전부 깨졌나
4. `UserDetailsService` 빈이 두 개일 때 스프링은 어떻게 동작했나. 왜 에러가 아니라 경고였을까
5. 회원가입이 401이었는데 진짜 원인은 무엇이었고, 왜 403이 아니라 401로 보였나
6. 세션 로그인 뒤 비밀번호 없이 내 정보가 보인 이유는? 서버는 "누구인지"를 어디에 저장하고 어디서 꺼냈나
7. 로그인 성공 시 세션 ID를 바꾸는 이유는? (세션 고정 공격)
8. 세션 로그인에서 CORS 설정을 한 줄 더 해야 했던 이유는?
