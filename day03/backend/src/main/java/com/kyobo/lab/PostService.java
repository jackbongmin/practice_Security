package com.kyobo.lab;


import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

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

@Service
public class PostService {

    private final PostRepository posts;

    public PostService(PostRepository posts) {
        this.posts = posts;
    }

    public List<Post> findAll() {
        return posts.findAll();
    }

    public Post create(String title, String author) {
        return posts.save(new Post(title, author));
    }

    // 작성자 본인 또는 관리자만. #id = 이 메서드의 id 파라미터, authentication.name = 로그인한 아이디
    @PreAuthorize("@postGuard.isAuthor(#id, authentication.name) or hasRole('ADMIN')")
    @Transactional  // 메서드가 끝날 때 바뀐 제목을 DB에 반영함
    public Post update(Long id, String title) {
        Post post = posts.findById(id).orElseThrow();   // 없는 번호면 예외
        post.setTitle(title);
        return post;
    }

    // 작성자 본인 또는 관리자만
    @PreAuthorize("@postGuard.isAuthor(#id, authentication.name) or hasRole('ADMIN')")
    public void delete(Long id) {
        posts.deleteById(id);
    }

    // URL 규칙이 없어도 메서드에서 막음 (ADMIN만)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAll() {
    }
}
