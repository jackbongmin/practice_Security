package com.kyobo.lab;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나)
 *
 * [역할] 게시글 테이블(POST). 제목과 작성자 아이디를 저장함
 *
 * [알아야 할 것]
 * - author 칸이 "내 글인지"를 판단하는 기준임 (PostGuard가 로그인한 아이디와 비교함)
 * - author는 글을 쓸 때 로그인 정보(Principal)에서 정함. 요청 본문에서 받지 않음 (가이드 1-6)
 * - 수정할 수 있는 것은 제목뿐이라 setter는 setTitle 하나만 둠. author는 바꿀 수 없음
 * - 응답할 때 이 객체가 그대로 JSON이 됨 → {"id":1,"title":"...","author":"user"} (getter 이름 기준)
 */

@Entity     // 이 클래스 = 테이블 하나(Member와 같은 방식)
public class Post {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;  // 작성자 아이디

    protected Post() {      // JPA 전용

    }

    public Post(String title, String author) {
        this.title = title;
        this.author = author;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }

    public void setTitle(String title) { this.title = title; } // 제목만 바꿀 수 있음
}
