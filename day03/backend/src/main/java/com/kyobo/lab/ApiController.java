package com.kyobo.lab;

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
public class ApiController {
}
