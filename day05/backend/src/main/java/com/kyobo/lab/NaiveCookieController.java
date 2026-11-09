package com.kyobo.lab;


import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 5일차 · 05 (같은 로그인, 세 가지 방법)
 *
 * [역할] 일부러 잘못 만든 "쿠키에 아이디만 저장하는" 로그인. 세션 · 토큰과 비교하려고 만듦 (실제로 쓰면 안 됨)
 *   - POST /api/public/naive/login : 아이디 · 비밀번호를 확인하고 쿠키 naive_user=아이디 를 내려 줌
 *   - GET  /api/public/naive/me    : 쿠키 naive_user 값을 그대로 믿고 "누구인지" 돌려줌
 *
 * [알아야 할 것]
 * - 브라우저에 둔 값은 사용자가 마음대로 바꿀 수 있음. 서버가 그 값을 확인 없이 믿는 것이 이 클래스의 구멍 (가이드 4-2)
 * - 주소가 /api/public/** 라서 SecurityConfig 규칙상 누구나 부를 수 있음. 로그인 여부는 이 클래스가 쿠키로 "직접" 판단함
 * - 로그인 POST는 4일차 CSRF 보호에 걸림 → 화면은 X-CSRF-TOKEN 헤더를 실어 보냄
 * - 비밀번호 확인은 직접 하지 않고 스프링 시큐리티의 AuthenticationManager에게 맡김 (JpaUserDetailsService + BCrypt)
 */

@RestController
public class NaiveCookieController {

    // 로그인 요청 본문 {"username":"user","password":"1234"}을 담은 그릇
    record LoginRequest(String username, String password) {

    }

    // AuthenticationManager를 꺼내 주는 스프링 시큐리티 부품. 우리 사용자(DB) + BCrypt로 확인하는 관리자를 줌
    private final AuthenticationConfiguration authConfig;

    public NaiveCookieController(AuthenticationConfiguration authConfig) {
        this.authConfig = authConfig;
    }

    @PostMapping("/api/public/naive/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest req) throws Exception {
        try {
            // 아이디 · 비밀번호 확인. 틀리면 AuthenticationException을 던짐 (2일차 폼 로그인이 하던 일을 직접 부름)
            authConfig.getAuthenticationManager().authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "아이디 또는 비밀번호가 틀립니다"));
        }
        // 구멍: 쿠키에 아이디를 "그대로" 넣음. 서명도 암호화도 없음
        ResponseCookie cookie = ResponseCookie.from("naive_user", req.username()).path("/").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString()) // 응답 헤더 Set-Cookie: naive_user=user; Path=/
                .body(Map.of("username", req.username()));
    }

    @GetMapping("/api/public/naive/me")
    public Map<String, Object> me(@CookieValue(name = "naive_user", required = false) String username) { // 요청의 쿠키 값을 꺼냄. 없으면 null
        if (username == null) {
            return Map.of("username", "(쿠키 없음)");
        }
        // 구멍: 쿠키 값이 진짜 로그인한 사람인지 확인하지 않고 그대로 믿음
        return Map.of("username", username, "isAdmin", username.equals("admin"));
    }
}
