package com.kyobo.lab;

/**
 * 1일차 · 01 (스프링 시큐리티 시작)
 *
 * [역할] 서버 시작점. bootRun을 하면 이 클래스의 main()이 실행되어 스프링 서버(8080)가 뜸
 *
 * [알아야 할 것]
 * - @SpringBootApplication은 이 클래스가 있는 패키지(com.kyobo.lab)와 그 아래만 훑어서
 *   @RestController, @Configuration 같은 클래스를 찾아 등록함
 * - 그래서 ApiController, WebConfig, SecurityConfig도 반드시 같은 패키지(또는 하위)에 둬야 함
 * - 이 클래스에는 보안 코드를 넣지 않음. 보안 설정은 SecurityConfig가 따로 맡음
 */
public class SecurityDay1Application {
}
