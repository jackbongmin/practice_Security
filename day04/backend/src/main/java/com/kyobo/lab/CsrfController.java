package com.kyobo.lab;


import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더)
 *
 * [역할] CSRF 토큰을 내주는 API (GET /api/public/csrf). React 화면이 POST · PUT · DELETE 전에 여기서 토큰을 받아 감
 *
 * [알아야 할 것]
 * - CSRF 보호를 켜면 GET을 뺀 모든 요청(로그인 포함)에 토큰이 있어야 함. 토큰을 받는 이 API는 GET이라 토큰 없이 부를 수 있음
 * - 주소가 /api/public/** 아래라서 로그인 안 해도 부를 수 있음 (로그인 요청 전에 토큰이 먼저 필요하기 때문)
 * - 공격자 사이트도 이 주소를 부를 수는 있지만, 다른 출처라서 응답(토큰)을 읽지 못함 (CORS, 가이드 4-8)
 */
@RestController
public class CsrfController {

    // 응답: {"parameterName":"_csrf","token":"...","headerName":"X-CSRF-TOKEN"}
    @GetMapping("/api/public/csrf")
    public CsrfToken csrf(CsrfToken token) {    // 파라미터에 CsrfToken을 쓰면 스프링 시큐리티가 지금 세션의 토큰을 넣어줌
        return token;                           // 그래도 돌려주면 JSON이 됨(hearderName = 화면이 토큰을 실을 헤더 이름)

    }

}
