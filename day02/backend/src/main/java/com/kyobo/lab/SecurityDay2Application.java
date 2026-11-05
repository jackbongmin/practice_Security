package com.kyobo.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 서버 시작점. 1일차 SecurityDay1Application과 하는 일이 같고 이름만 다름
 *
 * [알아야 할 것]
 * - 같은 패키지(com.kyobo.lab)의 @Entity, @Service, @Component, @RestController, @Configuration을 찾아 등록함
 * - 2일차부터 @Entity(Member)가 생기므로 서버가 뜰 때 JPA가 H2 DB에 테이블도 만듦
 */
@SpringBootApplication // 설정 읽기 + 같은 패키지의 빈 찾기 + 자동 설정(JPA·H2 포함)을 한 번에 켬
public class SecurityDay2Application {

    public static void main(String[] args) {
        SpringApplication.run(SecurityDay2Application.class, args);
    }
}