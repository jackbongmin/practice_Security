package com.kyobo.lab;

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
public class Member {
}
