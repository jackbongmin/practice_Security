package com.kyobo.lab;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


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
}
