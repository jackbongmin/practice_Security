package com.kyobo.lab;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인): PasswordEncoder 추가, 메모리 사용자 삭제, CSRF 끔, 폼 로그인을 React용으로 바꿈
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나): HTTP 메서드별 규칙, 역할 계층, 메서드 보안 스위치, 401·403 JSON 응답
 *
 * [역할] 보안 설정의 중심. 스프링 시큐리티에게 알려 주는 것
 *   1. 주소 + HTTP 메서드별 규칙 (문 앞 검사)            → filterChain()
 *   2. 거절됐을 때 응답 모양 (401 · 403 JSON)              → filterChain()의 exceptionHandling
 *   3. 로그인 방식 (HTTP Basic, 세션 로그인, 로그아웃)       → filterChain()
 *   4. 역할 계층 (ADMIN > USER), 비밀번호 비교 (BCrypt)      → roleHierarchy(), passwordEncoder()
 *   5. 메서드 보안 켜기 (@PreAuthorize 동작)                → @EnableMethodSecurity
 *
 * [알아야 할 것]
 * - URL 규칙은 "누구의 글인지"를 모름. 소유자 검사는 PostService의 @PreAuthorize + PostGuard가 함
 * - @EnableMethodSecurity가 빠지면 @PreAuthorize가 전부 조용히 무시됨. 에러도 경고도 없음 (4-4)
 * - exceptionHandling의 두 처리기는 URL 규칙 거절과 메서드 보안 거절을 모두 같은 모양으로 바꿈
 * - CSRF는 아직 꺼 둠. 4일차에 토큰 방식으로 다시 켬
 */
public class SecurityConfig {
}
