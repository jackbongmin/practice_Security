package com.kyobo.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나)
 *
 * [역할] 서버 시작점. 1·2일차와 하는 일이 같고 이름만 다름 (2일차 파일에서 이름만 바꿔 미리 만들어 둠)
 *
 * [알아야 할 것]
 * - 같은 패키지의 @Entity(Member, Post), @Service, @Component, @RestController, @Configuration을 찾아 등록함
 * - 메서드 보안 스위치(@EnableMethodSecurity)는 여기가 아니라 SecurityConfig에 붙임 (4-5)
 */
@SpringBootApplication // 설정 읽기 + 같은 패키지의 빈 찾기 + 자동 설정(JPA·H2 포함)을 한 번에 켬
public class SecurityDay3Application {

    public static void main(String[] args) {
        SpringApplication.run(SecurityDay3Application.class, args);
    }
}