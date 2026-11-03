package com.kyobo.lab;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

	// 누구나 봐도 되는 정보
	@GetMapping("/public/notices")
	public List<Map<String, Object>> notices() {
		return List.of(
				Map.of("id", 1, "title", "11월 보안 교육 안내"),
				Map.of("id", 2, "title", "비밀번호 변경 주기 안내"));
	}

	// 로그인한 사용자 본인 정보
	@GetMapping("/me")
	public Map<String, String> me(Principal principal) {
		return Map.of("username", principal == null ? "(로그인 정보 없음)" : principal.getName());
	}

	// 관리자만 봐야 하는 정보
	@GetMapping("/admin/stats")
	public Map<String, Object> adminStats() {
		return Map.of("members", 128, "todaySales", 3_450_000);
	}
}
