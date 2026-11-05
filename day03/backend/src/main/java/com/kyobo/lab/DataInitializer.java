package com.kyobo.lab;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나): kim 회원과 글 2개 추가
 *
 * [역할] 서버가 뜰 때 DB에 회원 3명(user, kim, admin)과 글 2개(1번 user의 글, 2번 kim의 글)를 넣음
 *
 * [알아야 할 것]
 * - H2 메모리 DB라 서버를 재시작하면 처음 상태(회원 3명, 글 2개)로 돌아감. 삭제 실험 뒤 되살릴 때 씀
 * - 글 번호는 넣은 순서대로 1, 2가 됨. 4장의 "1번 글 = user의 글"이 이 순서에서 나옴
 * - kim은 "같은 USER인데 남의 글"을 확인하려고 넣음
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository members;
    private final PostRepository posts; // ← 3일차 추가: 글을 저장하려고
    private final PasswordEncoder encoder;

    public DataInitializer(MemberRepository members, PostRepository posts, PasswordEncoder encoder) { // ← 3일차: posts 파라미터 추가
        this.members = members;
        this.posts = posts; // ← 3일차 추가
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        members.save(new Member("user", encoder.encode("1234"), "USER"));
        members.save(new Member("kim", encoder.encode("1234"), "USER"));   // ← 3일차 추가: 같은 USER인 다른 사람
        members.save(new Member("admin", encoder.encode("1234"), "ADMIN"));
        posts.save(new Post("user의 첫 글", "user")); // ← 3일차 추가: 1번 글 (주인 user)
        posts.save(new Post("kim의 첫 글", "kim"));   // ← 3일차 추가: 2번 글 (주인 kim)
    }
}