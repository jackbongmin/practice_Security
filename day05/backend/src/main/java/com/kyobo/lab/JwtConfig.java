package com.kyobo.lab;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * 5일차 · 05 (같은 로그인, 세 가지 방법)
 *
 * [역할] 토큰(JWT) 방식의 보안 설정
 *   1. 서명 키 (application.properties의 jwt.secret)   → 생성자
 *   2. 토큰 만들기(서명) / 검사하기(서명 · 만료)         → jwtEncoder(), jwtDecoder()
 *   3. /api/token/** 전용 보안 필터 체인                → tokenChain()
 *   4. 토큰의 roles 클레임을 ROLE_ 권한으로 바꾸기        → rolesConverter()
 *
 * [알아야 할 것]
 * - SecurityConfig의 filterChain과 별개로 체인이 하나 더 생김. 요청마다 "처음 맞는 체인 하나"만 씀 (1일차) → @Order(1)로 먼저 검사
 * - 토큰 체인은 쿠키 · 세션을 쓰지 않음 → CSRF 대상이 아니고(끔), 세션도 만들지 않음(STATELESS)
 * - 서명 키를 아는 쪽만 토큰을 만들 수 있음. 키가 새면 누구나 관리자 토큰을 만듦 (운영에서는 환경 변수 · 비밀 저장소에 둠)
 */

@Configuration  // 스프링 설정 클래스. 안의 @Bean 메서드가 만든 객체를 스프링이 보관함
public class JwtConfig {

    private final SecretKey key;

    // 서명 키 : application.properties의 jwt.secret글자를 HS256용 키로 바꿈. 서버만 알아야함.
    public JwtConfig(@Value("${jwt.secret}") String secret) {   // @Value: 설정 파일 값을넣어줌. 줄이 없으면 Could not resolve placegolder 'jwt.secret'
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    // 토큰 만들기(서명). TockenController가 이 빈을 받아씀
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));    // Nimbus = 스프링이 이 안에서 쓰는 JWT 라이브러리
    }

    // /api/tocken/** 요청만 맡는 두 번째 보안 필터 체인
    @Bean
    @Order(1)   // 4일차 SecurityConfig의 체인보다 먼저 검사함(숫자가 작을수록 먼저)
    public SecurityFilterChain tockenChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/token/**")       // 이 체인이 맡을 주소. 빠뜨리면 모든 요청을 가져가서 세션 로그인이 망가짐
                .cors(Customizer.withDefaults())                // 화면(3000)에서 부를 수 있게 WebConfig의 CORS 규칙을 씀
                .csrf(csrf -> csrf.disable())           // 쿠키를 안 쓰는 방식이라 CSRF 대상이 아님 (가이드 1-3)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션을 만들지도 쓰지도 않음
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/token/login").permitAll()   // 토큰을 받으러 오는 곳은 누구나
                        .anyRequest().authenticated());                             // 나머지 /api/token/**는 로그인(토큰) 필요
        return http.build();
    }
}
