package com.kyobo.lab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.beans.BeanProperty;


/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 *
 * [역할] 오늘의 핵심 클래스. 스프링 시큐리티에게 세 가지를 알려 줌
 *   1. 주소별 규칙 (누가 어디에 들어갈 수 있나) → filterChain()
 *   2. 로그인 방식 (HTTP Basic, 폼 로그인)      → filterChain()
 *   3. 로그인할 수 있는 사용자 목록             → userDetailsService()
 *
 * [알아야 할 것]
 * - @Bean 메서드가 돌려준 객체를 스프링이 들고 있다가 보안 필터 체인에 씀. 직접 호출하는 곳은 없음
 * - 이 클래스가 없으면 "전부 로그인 필요 + 임시 비밀번호" 기본 설정으로 동작함 (4-2)
 * - 규칙은 위에서부터 차례로 보고 처음 맞는 하나만 씀. anyRequest()는 항상 맨 마지막
 * - 스프링 시큐리티 6 문법임. 블로그의 WebSecurityConfigurerAdapter, antMatchers, .and()는 옛날 문법
 */

@Configuration      // 서버가 뜰 때 이 클래스의 @Bean 메서드들을 실행해서 결과를 등록함
public class SecurityConfig {

    // 보안 필터 묶음(SecurityFilterChain)을 만들어 등록함. 모든 요청이 컨트롤러 전에 이 필터들을 지남
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // WebConfig의 CORS설정을 보안 검사보다 먼저 적용함. 사전 요청(OPTION)dl 401로 막히지 않게
                .cors(Customizer.withDefaults())
                // 주소별 인가 규칙. 위에서부터 차례로 보고 처음 맞는 규칙 하나만 씀
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()  // 공지는 로그인 없이 통과
                        // 로그인한 사람중에서도 ADMIN만 허용. 좁은 규칙이라 anyRequest()보다 위에 둠
                        // hasRole("ADMIN") -> 내부의 ROLE_ADMIN과 비교함(ROLE_은 자동으로 붙음)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())                  // 나머지는 로그인만 하면 누구든 통과(항상 맨 마지막)
                .httpBasic(Customizer.withDefaults())                   // Authorization : Basic 헤더로 로그인(React 화면용)
                .formLogin(Customizer.withDefaults());                  // login 화면으로 로그인(주소창용)

        return http.build();    // 위 설정대로 필터 묶음을 완성
    }

    // 로그인할 수 있는 사용자 목록. 로그인할 때 스프링 시큐리티가 여기서 아이디로 사용자를 찾음
    // 1일차는 메모리 사용자 2명. 2일차에 DB 사용자와 BCrypt로 바꿈
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(  // 서버 메모리에 저장. 서버를 끄면 사라짐
                // {noop} = 암호화 안함 표시. 빠지면 "PasswordEncoder mapped for the id null" 오류
                // roles("USER") -> 내부에는 ROLE_USER로 저장됨
                User.withUsername("user").password("{noop}1234").roles("USER").build(),
                User.withUsername("admin").password("{noop}1234").roles("ADMIN").build());
    }
}
