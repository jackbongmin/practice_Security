# practice_Security

스프링 시큐리티 수업 일자별 실습 저장소.

## 폴더 구조

```
practice_Security/
├─ _template/
│  └─ GUIDE_TEMPLATE.md   # 일자별 가이드 공통 양식
└─ dayNN/
   ├─ GUIDE.md            # 내가 정리하는 가이드 (양식 통일)
   ├─ 원본_튜토리얼.md      # 수업 자료 원본
   ├─ backend-start/      # 실습하며 직접 수정하는 코드
   ├─ backend-final/      # 완성 코드 (막히면 비교)
   └─ frontend/           # React 화면
```

## 가이드 양식

1. **Part 1. 개념과 용어** — 처음 보는 사람 기준으로 쉽게
2. **Part 2. 기능별 클래스 작성 가이드** — 기능마다 파일·역할·작성 순서·코드·확인 방법·막히는 곳
3. **Part 3. 오늘 정리** — 체크리스트, 회고, 다음 예고

## 일정

| 일차 | 날짜 | 주제 | 폴더 |
|---|---|---|---|
| 1 | 09_01 | 스프링 시큐리티 켜고, 막혀 보고, 필요한 만큼 열기 | [day01](day01/GUIDE.md) |
| 2 | 09_02 | BCrypt · DB 사용자 · 세션 로그인 | - |
| 3 | 09_03 | 인가: 누가 무엇을 할 수 있나 | - |
| 4 | 09_04 | XSS · CSRF · CORS · 보안 헤더 | - |
| 5 | 09_05 | 같은 로그인, 세 가지 방법 (세션 / 토큰 등) | - |

## 실행

```bash
# 서버 (Windows 명령 프롬프트는 gradlew.bat)
cd dayNN/backend-start
./gradlew bootRun

# 화면
cd dayNN/frontend
npm install
npm run dev
```
