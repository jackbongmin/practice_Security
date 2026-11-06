package com.kyobo.lab;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;


/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더): changePassword 추가
 *
 * [역할] 회원 테이블(MEMBER). 객체 하나 = 회원 한 명 = 테이블 한 줄. 아이디 · 비밀번호 해시 · 역할을 저장함
 *
 * [알아야 할 것]
 * - @Entity를 붙이면 서버가 뜰 때 JPA가 이 모양대로 H2 DB에 테이블을 만듦 (서버를 끄면 사라짐)
 * - password 칸에는 평문이 아니라 BCrypt 해시($2a$10$...)가 들어감. 평문을 넣는 코드는 어디에도 없어야 함
 * - role에는 "USER" 또는 "ADMIN"만 넣음. ROLE_ 접두사는 로그인할 때 roles(...)가 붙여 줌
 * - 파라미터 없는 생성자(protected)는 JPA가 DB에서 꺼내 객체를 만들 때 씀. 지우면 오류 남
 */

@Entity     // 이 클래스 = 테이블 하나. 필드 하나 = 칸 하나
public class Member {

    @Id             // 기본 키(줄마다 다른 번호)
    @GeneratedValue // 번호는 DB가 자동으로 매김
    private Long id;

    @Column(unique = true, nullable = false)    // 같은 아이디 두번 저장 불가, 빈 값 불가
    private String username;

    @Column(nullable = false)
    private String password;    // BCrypt 해시

    @Column(nullable = false)
    private String role;        // USER 또는 ADMIN

    protected Member() {        // JPA 전용. 직접 쓰진 않음
    }

    public Member(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }

    public void changePassword(String encoded) {this.password = encoded;}   // 해시를 받아 바꿈
}
