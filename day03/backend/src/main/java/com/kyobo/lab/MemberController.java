package com.kyobo.lab;

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
public class MemberController {
}
