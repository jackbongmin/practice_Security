package com.kyobo.lab;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 *
 * [역할] 지킬 대상인 API 3개. 보호 수준이 서로 다름
 *   - GET /api/public/notices : 누구나
 *   - GET /api/me             : 로그인한 사람
 *   - GET /api/admin/stats    : 관리자(ADMIN)
 *
 * [알아야 할 것]
 * - 이 클래스에는 보안 코드가 한 줄도 없음. 막고 여는 일은 컨트롤러 앞의 보안 필터(SecurityConfig)가 함
 * - 주소를 보호 수준별로 /public, /admin 처럼 나눠 둔 덕분에 SecurityConfig에서 주소 규칙 한 줄로 묶을 수 있음
 * - 2일차에도 그대로 씀. 바뀌는 것은 "누구로 로그인했나"를 찾는 방법(DB)과 기억하는 방법(세션)뿐임
 */
@RestController         // 메서드가 돌려준 값을 JSON으로 바꿔 응답함
@RequestMapping("/api") // 이 클래스의 모든 주소 앞에 /api가 붙음
public class ApiController {

    // 누구나 봐도 되는 정보 → SecurityConfig에서 permitAll()
    @GetMapping("/public/notices")
    public List<Map<String, Object>> notices() {
        return List.of(
                Map.of("id", 1, "title", "11월 보안 교육 안내"),
                Map.of("id", 2, "title", "비밀번호 변경 주기 안내"));
    }

    // 로그인한 사용자 본인 정보 → SecurityConfig에서 authenticated()
    @GetMapping("/me")
    public Map<String, String> me(Principal principal) { // 지금 로그인한 사람. Basic이든 세션이든 스프링이 넣어 줌
        return Map.of("username", principal == null ? "(로그인 정보 없음)" : principal.getName());
    }

    // 관리자만 봐야 하는 정보 → SecurityConfig에서 hasRole("ADMIN")
    @GetMapping("/admin/stats")
    public Map<String, Object> adminStats() {
        return Map.of("members", 128, "todaySales", 3_450_000);
    }
}