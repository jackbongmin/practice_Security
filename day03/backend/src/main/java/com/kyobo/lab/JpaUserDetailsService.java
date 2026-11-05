package com.kyobo.lab;

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
public class JpaUserDetailsService {
}
