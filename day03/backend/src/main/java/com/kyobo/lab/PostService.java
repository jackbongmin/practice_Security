package com.kyobo.lab;

/**
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나)
 *
 * [역할] 게시글 업무 로직 (목록 · 쓰기 · 수정 · 삭제 · 전체 삭제). 메서드 보안(@PreAuthorize)이 붙는 곳
 *
 * [알아야 할 것]
 * - 권한 검사를 컨트롤러가 아니라 여기(서비스)에 붙임. 어느 컨트롤러에서 불러도 같은 검사를 거치게 하려고
 * - @PreAuthorize는 SecurityConfig의 @EnableMethodSecurity가 있어야만 동작함. 없으면 조용히 무시됨 (4-4)
 * - 검사는 다른 빈(PostController)이 부를 때만 됨. 이 클래스 안에서 this.delete()처럼 부르면 검사가 빠짐 (가이드 1-4)
 * - update는 @Transactional이라 post.setTitle()만 해도 메서드가 끝날 때 DB에 반영됨 (save를 따로 안 부름)
 */
public class PostService {
}
