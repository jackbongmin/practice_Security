package com.kyobo.lab;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
@RestController
@RequestMapping("/api/posts") // 이 클래스의 모든 주소 앞에 /api/posts
public class PostController {

    // 요청 본문 {"title":"..."}. author를 일부러 안 받음
    record PostRequest(String title) {
    }

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @GetMapping
    public List<Post> list() {
        return service.findAll();
    }

    // 작성자는 요청 본문이 아니라 로그인 정보에서 정함 (남의 이름으로 글쓰기 방지)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // 성공하면 200 대신 201
    public Post create(@RequestBody PostRequest req, Principal principal) {
        return service.create(req.title(), principal.getName());
    }

    @PutMapping("/{id}")
    public Post update(@PathVariable Long id, @RequestBody PostRequest req) { // @PathVariable: 주소의 {id} 값
        return service.update(id, req.title());
    }

    @DeleteMapping("/{id}")
    public Map<String, Long> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    // 전체 삭제: URL 규칙에는 따로 없음. 메서드 보안이 막음 (4-4, 4-5)
    @DeleteMapping
    public Map<String, String> deleteAll() {
        service.deleteAll();
        return Map.of("deleted", "all");
    }
}