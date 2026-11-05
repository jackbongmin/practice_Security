package com.kyobo.lab;

/**
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나)
 *
 * [역할] 게시글 테이블을 다루는 창구. save · findAll · findById · deleteById · deleteAll을 그대로 씀
 *
 * [알아야 할 것]
 * - 메서드를 하나도 안 써도 JpaRepository에 기본 기능이 다 들어 있음
 * - PostService(글 다루기)와 PostGuard(작성자 확인) 두 곳에서 씀
 */
public interface PostRepository {
}
