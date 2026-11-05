package com.kyobo.lab;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 * 2일차 · 02 (BCrypt · DB 사용자 · 세션 로그인): PasswordEncoder 추가, 메모리 사용자 삭제, CSRF 끔, 폼 로그인을 React용으로 바꿈
 *
 * [역할] 보안 설정의 중심. 스프링 시큐리티에게 세 가지를 알려 줌
 *   1. 주소별 규칙 (누가 어디에 들어갈 수 있나)                  → filterChain()
 *   2. 로그인 방식 (HTTP Basic, 세션 로그인 /api/login, 로그아웃)   → filterChain()
 *   3. 비밀번호 비교 방식 (BCrypt)                               → passwordEncoder()
 *
 * [알아야 할 것]
 * - 2일차 끝에는 사용자 목록이 이 클래스에 없음. JpaUserDetailsService(@Service)가 DB에서 찾음
 * - UserDetailsService 빈이 두 개면 스프링이 둘 다 안 씀 → 메모리 사용자 메서드는 반드시 지움 (4-4)
 * - CSRF를 잠시 끔. POST 회원가입이 막히던 원인. 4일차에 토큰 방식으로 다시 켬
 * - 규칙은 위에서부터 처음 맞는 하나만 씀. anyRequest()는 항상 맨 마지막 (1일차와 같음)
 */

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())                                    // cors 필터를 킴, Customizer.withDefaults()는 기본값을 사용한다는거
                // CSRF 보호를 끔. POST 회원가입이 403(→401)으로 막히던 원인. 4일차에 토큰 방식으로 다시 켬
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth     // 주소별 인가 규칙을 사용
                        .requestMatchers("/api/public/**").permitAll()          // /api/public/ 으로 시작하는 모든 주소는 로그인 없이 통과
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")      // /api/admin/ 아래는 ADMIN역할을 가진 사람만 통과
                        .anyRequest().authenticated())                                  // 위 규칙에 안걸린 나머지 모든 주소는 로그인만하면 통과
                .httpBasic(Customizer.withDefaults())                               // HTTP Basic 로그인을 켬
                // 세션 로그인: POST /api/login으로 아이디·비밀번호를 받아 확인하고, 성공하면 로그인 상태를 세션에 저장함
                .formLogin(form -> form
                        .loginProcessingUrl("/api/login")                             // 로그인 요청을 받을 주소 (기본은 /login)
                        .successHandler((req, res, auth) -> res.setStatus(200))       // 성공: 페이지 이동(302) 대신 200만
                        .failureHandler((req, res, e) -> res.setStatus(401)))         // 실패: 로그인 화면 이동 대신 401만
                // 로그아웃: POST /api/logout으로 세션을 없앰
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)));
        return http.build();
    }

//    // 1일차 완성 상태: 메모리 사용자 2명, 비밀번호는 평문({noop}). 3-4에서 해시로, 3-6에서 통째로 지움
//    @Bean
//    public UserDetailsService userDetailsService() {
//        return new InMemoryUserDetailsManager(
//                User.withUsername("user").password("{noop}1234").roles("USER").build(),
//                User.withUsername("admin").password("{noop}1234").roles("ADMIN").build());
//    }

//    // 메모리 사용자 2명. 비밀번호를 평문 대신 해시로 넣음 (3-6에서 통째로 지움)
//    @Bean
//    public UserDetailsService userDetailsService(PasswordEncoder encoder) { // 3-3의 빈을 스프링이 넣어 줌
//        return new InMemoryUserDetailsManager(
//                // encoder.encode("1234") → "$2a$10$..." 해시가 저장됨. 서버를 켤 때마다 다른 값
//                User.withUsername("user").password(encoder.encode("1234")).roles("USER").build(),
//                User.withUsername("admin").password(encoder.encode("1234")).roles("ADMIN").build());
//    }

    // 비밀번호를 BCrypt 해시로 만들고(encode) 비교함(matches). 이 빈이 생기면 모든 로그인 비교가 BCrypt로 바뀜
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 기본 강도 10 (2¹⁰번 반복)
    }

}
