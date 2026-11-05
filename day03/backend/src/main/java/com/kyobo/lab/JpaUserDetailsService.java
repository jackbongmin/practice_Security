package com.kyobo.lab;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인)
 *
 * [역할] 로그인할 때 스프링 시큐리티가 "이 아이디의 사용자 정보 줘"라고 부르는 창구의 DB 구현
 *   → 1일차 InMemoryUserDetailsManager(메모리 사용자) 자리를 대신함
 *
 * [알아야 할 것]
 * - @Service로 빈이 되면 스프링이 알아서 로그인 처리기(DaoAuthenticationProvider)에 꽂음. 직접 부르는 곳은 없음
 * - 단, UserDetailsService 빈이 이것 하나뿐이어야 함. SecurityConfig에 메모리 사용자가 남아 있으면 둘 다 안 씀 (4-4)
 * - 비밀번호 비교는 여기서 하지 않음. DB의 해시를 그대로 넘기면 PasswordEncoder.matches()가 비교함
 * - 없는 아이디면 UsernameNotFoundException을 던짐 → 스프링이 401로 바꿈
 */

@Service        // 이 클래스를 빈으로 등록함(@Component와 같은데 "업루 로직"이라는 표시)
public class JpaUserDetailsService implements UserDetailsService {

    private final MemberRepository members;

    public JpaUserDetailsService(MemberRepository members) { // 생성자 주입: 스프링이 MemberRepository 빈을 넣어 줌
        this.members = members;
    }

    // 로그인할 때마다 스프링 시큐리티가 부름. 입력한 아이디가 username으로 돌아옴
    @Override
    public UserDetails loadUserByUsername(String username) {
        Member m = members.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username)); // 없는 아이디 → 로그인 실패(401)
        // DB의 Member를 스프링 시큐리티가 아는 모양(UserDetails)으로 바꿔서 돌려줌
        return User.withUsername(m.getUsername())
                .password(m.getPassword()) // DB의 해시 그대로. 비교는 PasswordEncoder가 함
                .roles(m.getRole())        // "USER" → 내부에는 ROLE_USER
                .build();
    }
}
