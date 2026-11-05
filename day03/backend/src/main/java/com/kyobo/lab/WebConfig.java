package com.kyobo.lab;

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
public class WebConfig {
}
