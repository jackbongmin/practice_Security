package com.kyobo.lab;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 서버가 뜰 때 DB에 처음 회원 두 명(user, admin)을 넣음
 *
 * [알아야 할 것]
 * - CommandLineRunner의 run()은 서버가 다 뜬 직후 한 번 실행됨
 * - H2 메모리 DB라 서버를 재시작하면 DB가 비고, 다시 이 두 명만 들어감 (회원가입한 사람은 사라짐)
 * - 비밀번호는 반드시 encoder.encode()로 해시해서 넣음. 평문을 넣으면 BCrypt 비교에서 로그인이 안 됨
 */

@Component  // 빈으로 등록함. 그래야 스프링이 run()을 불러줌
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository members;
    private final PasswordEncoder encoder;

    public DataInitializer(MemberRepository members, PasswordEncoder encoder) {
        this.members = members;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        members.save(new Member("user", encoder.encode("1234"), "USER"));
        members.save(new Member("admin", encoder.encode("1234"), "ADMIN"));
    }
}
