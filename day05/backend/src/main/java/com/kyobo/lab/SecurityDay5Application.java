package com.kyobo.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 5일차 · 05 (같은 로그인, 세 가지 방법)
 *
 * [역할] 서버 시작점. 1~4일차와 하는 일이 같고 이름만 다름 (4일차 파일에서 이름만 바꿔 미리 만들어 둠)
 *
 * [알아야 할 것]
 * - 같은 패키지의 @Entity, @Service, @Component, @RestController, @Configuration을 찾아 등록함
 * - 오늘 새로 쓰는 NaiveCookieController · JwtConfig · TokenController도 같은 패키지라 따로 등록하지 않아도 됨
 */
@SpringBootApplication // 설정 읽기 + 같은 패키지의 빈 찾기 + 자동 설정(JPA·H2 포함)을 한 번에 켬
public class SecurityDay5Application {

    public static void main(String[] args) {
        SpringApplication.run(SecurityDay5Application.class, args);
    }
}
