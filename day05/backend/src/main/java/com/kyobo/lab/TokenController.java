package com.kyobo.lab;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 5일차 · 05 (같은 로그인, 세 가지 방법)
 *
 * [역할] 토큰(JWT) 방식 API 세 개
 *   - POST /api/token/login : 아이디 · 비밀번호 확인 후 서명한 토큰을 돌려줌 (누구나)
 *   - GET  /api/token/me    : 토큰에서 꺼낸 정보 (토큰 있는 사람)
 *   - GET  /api/token/admin : 관리자 전용 (ADMIN 토큰)
 *
 * [알아야 할 것]
 * - 서버는 로그인 상태를 아무 데도 저장하지 않음. 화면이 토큰을 들고 있다가 요청마다 Authorization: Bearer 헤더에 실음
 * - /me는 DB도 세션도 보지 않음. 서명이 맞는 토큰이면 그 안에 적힌 내용을 그대로 믿음
 * - 토큰 가운데 조각(내용)은 암호화가 아니라 Base64URL 인코딩임 → 누구나 읽음. 비밀번호 · 개인정보를 넣지 않음
 */
@RestController
public class TokenController {

    // 로그인 요청 본문 {"username":"user","password":"1234"}을 담는 그릇
    record LoginRequest(String username, String password) {

    }

    private final AuthenticationConfiguration authConfig;
    private final JwtEncoder encoder;
    private final long ttlSeconds;

    public TokenController(AuthenticationConfiguration authConfig, JwtEncoder encoder,
                           @Value("${jwt.ttl-seconds}") long ttlSeconds) { // 토큰 수명(초). 설정 파일의 jwt.ttl-seconds
        this.authConfig = authConfig;
        this.encoder = encoder;
        this.ttlSeconds = ttlSeconds;
    }

    // 아이디 비밀번호 확인 -> 서명한 토큰을 돌려줌. 서버는 아무것도 저장하지 않음
    @PostMapping("/api/token/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest req) throws Exception {
        Authentication auth;
        try {
            // 4-2와 같은 방법으로 비밀번호 확인. 성공하면 이름 권한(ROLE_USER ...)이 든 Authentication이 나옴
            auth = authConfig.getAuthenticationManager().authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "아이디 또는 비밀번호가 틀립니다."));
        }

        // 권한 ROLE_USER -> "USER"로 바꿔 토큰에 넣음(토큰에는 짧은 이름만)
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a->a.replace("ROLE_", ""))
                .toList();
        Instant now = Instant.now();
        // 토큰 내용(클레임). 누구나 읽을 수 있으니 비밀번호는 절대 넣지않음
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("security-day5")                 // iss : 누가 발급했나
                .subject(auth.getName())                 // sub: 누구의 토큰인가
                .issuedAt(now)                           // iat: 언제 발급했나
                .expiresAt(now.plusSeconds(ttlSeconds))  // exp: 언제까지 (지금 + 600초)
                .claim("roles", roles)                   // roles: 무슨 역할인가 (우리가 정한 이름)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build(); // 헤더 {"alg":"HS256"}: 키 하나로 서명
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(); // 헤더.내용.서명 문자열
        return ResponseEntity.ok(Map.of("token", token));
    }

}
