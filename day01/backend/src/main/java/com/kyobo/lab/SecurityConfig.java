package com.kyobo.lab;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 *
 * [역할] 오늘의 핵심 클래스. 스프링 시큐리티에게 세 가지를 알려 줌
 *   1. 주소별 규칙 (누가 어디에 들어갈 수 있나) → filterChain()
 *   2. 로그인 방식 (HTTP Basic, 폼 로그인)      → filterChain()
 *   3. 로그인할 수 있는 사용자 목록             → userDetailsService()
 *
 * [알아야 할 것]
 * - @Bean 메서드가 돌려준 객체를 스프링이 들고 있다가 보안 필터 체인에 씀. 직접 호출하는 곳은 없음
 * - 이 클래스가 없으면 "전부 로그인 필요 + 임시 비밀번호" 기본 설정으로 동작함 (4-2)
 * - 규칙은 위에서부터 차례로 보고 처음 맞는 하나만 씀. anyRequest()는 항상 맨 마지막
 * - 스프링 시큐리티 6 문법임. 블로그의 WebSecurityConfigurerAdapter, antMatchers, .and()는 옛날 문법
 */
public class SecurityConfig {
}
