package com.kyobo.lab;

import java.security.Principal;   // ← 4일차
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;   // ← 4일차
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;       // ← 4일차
import org.springframework.web.bind.annotation.RestController;


/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 * 4일차 · 04 (XSS · CSRF · CORS · 보안 헤더): 비밀번호 변경 추가
 *
 * [역할] 회원 API 세 개
 *   - POST /api/public/signup   : 회원가입. 비밀번호를 해시로 바꿔서 저장함 (누구나)
 *   - GET  /api/admin/members   : DB에 저장된 회원 목록. 해시가 어떻게 생겼는지 보는 실습용 (관리자)
 *   - POST /account/password    : 로그인한 사람의 비밀번호 변경 (로그인한 사람). 오늘 CSRF 공격 대상
 *
 * [알아야 할 것]
 * - 가입할 때 역할은 요청에서 받지 않고 항상 "USER"로 정함. 요청에서 받으면 누구나 ADMIN으로 가입할 수 있음
 * - 비밀번호 변경은 "누구의 비밀번호인지"를 요청에서 받지 않고 Principal(로그인 정보)에서 꺼냄 (3일차 작성자와 같은 이유)
 * - /account/** 주소는 WebConfig의 CORS 설정(/api/**)에 안 걸림. 폼 POST는 CORS와 상관없이 서버까지 감 (가이드 1-5)
 * - 그래서 이 API를 지키는 것은 CSRF 토큰뿐임. 4-6 전까지는 공격자 사이트에서도 비밀번호가 바뀜 (4-5)
 * - 회원 목록은 실습용임. 실제 서비스는 해시도 밖으로 내보내지 않음
 */

@RestController
public class MemberController {

    // 회원가입 요청 본문 {"username":"kim","password":"1234"}을 담는 그릇
    record SignupRequest(String username, String password) {

    }

    private final MemberRepository members;
    private final PasswordEncoder encoder;

    public MemberController(MemberRepository members, PasswordEncoder encoder) {
        this.members = members;
        this.encoder = encoder;
    }

    // 회원가입. 비밀번호는 해시로 바꿔서 저장함
    @PostMapping("/api/public/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest req) { // @RequestBody: 본문 JSON → SignupRequest
        // 빈 아이디 · 4자 미만 비밀번호 거절 → 400
        if (req.username() == null || req.username().isBlank() || req.password() == null || req.password().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "아이디와 4자 이상 비밀번호가 필요합니다"));
        }
        // 이미 있는 아이디 → 409 Conflict
        if (members.existsByUsername(req.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "이미 있는 아이디입니다"));
        }
        // 평문이 아니라 해시를 저장함. 역할은 항상 USER
        members.save(new Member(req.username(), encoder.encode(req.password()), "USER"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", req.username())); // 201 Created

    }

    // DB에 무엇이 저장됐는지 보는 관리자용 API (실습용 — 실제 서비스는 해시도 밖으로 내보내지 않음)
    @GetMapping("/api/admin/members")
    public List<Map<String, String>> list() {
        return members.findAll().stream()
                .map(m -> Map.of("username", m.getUsername(), "role", m.getRole(), "password", m.getPassword()))
                .toList();
    }


    @PostMapping("/account/password")
    @Transactional  // 메서드가 끝날 때 바뀐 비밀번호를 DB에 반영함
    public Map<String, String> changePassword(@RequestParam String newPassword, Principal principal) {  // @RequestParam: 폼 본문 newPassword=. . . 값
        Member m = members.findByUsername(principal.getName()).orElseThrow();   // 누구의 비밀번호인지 로그인 정보에서
        m.changePassword(encoder.encode(newPassword));                          // 평문이 아니라 해시로 바꿈
        return Map.of("changed", principal.getName());
    }
}
