package com.kyobo.lab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// 시작 코드: 보안이 없어서 관리자 통계까지 누구나 볼 수 있다
@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void 보안이_없으면_모든_API가_열려_있다() throws Exception {
		mvc.perform(get("/api/public/notices")).andExpect(status().isOk());
		mvc.perform(get("/api/me")).andExpect(status().isOk());
		mvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
	}
}
