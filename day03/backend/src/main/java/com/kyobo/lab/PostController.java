package com.kyobo.lab;

/**
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나)
 *
 * [역할] 게시판 API 5개. 같은 주소 /api/posts에 HTTP 메서드만 다른 요청들이 모여 있음
 *   - GET    /api/posts      : 목록          - POST   /api/posts      : 글쓰기
 *   - PUT    /api/posts/{id} : 제목 수정     - DELETE /api/posts/{id} : 글 하나 삭제
 *   - DELETE /api/posts      : 전체 삭제
 *
 * [알아야 할 것]
 * - 이 클래스에도 보안 코드가 없음. URL 규칙은 SecurityConfig, 메서드 규칙은 PostService에 있음
 * - 작성자는 요청 본문이 아니라 Principal(로그인 정보)에서 꺼냄. 그래서 PostRequest에 author가 없음
 * - 전체 삭제(DELETE /api/posts)는 URL 규칙을 따로 안 만듦. 메서드 보안이 막는 것을 보려고 일부러 둠 (4-4, 4-5)
 */
public class PostController {
}
