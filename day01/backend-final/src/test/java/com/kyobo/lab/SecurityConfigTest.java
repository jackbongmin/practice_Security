package com.kyobo.lab;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// 완성 코드: 튜토리얼 3~5단계의 확인 내용을 그대로 검사한다
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	static final String FRONT = "http://localhost:3000";

	@Autowired
	MockMvc mvc;

	@Test
	void 공개_API는_로그인_없이_200() throws Exception {
		mvc.perform(get("/api/public/notices").header("Origin", FRONT))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", FRONT));
	}

	@Test
	void 로그인_없이_내_정보는_401이고_브라우저_로그인창을_띄우지_않는다() throws Exception {
		mvc.perform(get("/api/me").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}

	@Test
	void 로그인하면_내_정보가_보인다() throws Exception {
		mvc.perform(get("/api/me").with(httpBasic("user", "1234")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("user"));
	}

	@Test
	void 비밀번호가_틀리면_401() throws Exception {
		mvc.perform(get("/api/me").with(httpBasic("user", "wrong")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 일반_사용자는_관리자_통계에서_403() throws Exception {
		mvc.perform(get("/api/admin/stats").with(httpBasic("user", "1234")))
				.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_관리자_통계를_본다() throws Exception {
		mvc.perform(get("/api/admin/stats").with(httpBasic("admin", "1234")))
				.andExpect(status().isOk());
	}

	@Test
	void CORS_사전요청은_로그인_없이_통과한다() throws Exception {
		mvc.perform(options("/api/me")
				.header("Origin", FRONT)
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization,x-requested-with"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", FRONT));
	}
}
