package com.kyobo.lab;

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
public interface MemberRepository {
}
