package com.kyobo.lab;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 *
 * [역할] CORS 규칙. React 화면(localhost:3000)이 이 서버(localhost:8080)의 API를 부를 수 있게 허락함
 *
 * [알아야 할 것]
 * - 포트가 다르면 다른 출처(Origin)라서, 서버가 허락하지 않으면 브라우저가 응답을 막음 (가이드 1-5)
 * - 이 설정은 스프링 MVC(컨트롤러 쪽) 설정이라 보안 필터보다 "뒤"에서 동작함
 *   → 시큐리티를 켜면 사전 요청(OPTIONS)이 여기까지 오기 전에 401로 막힘 (4-2)
 *   → SecurityConfig의 .cors(...)가 이 규칙을 보안 필터 앞쪽으로 끌어와서 해결함 (3-5)
 * - CORS는 4일차에 자세히 다룸
 */
public class WebConfig {
}
