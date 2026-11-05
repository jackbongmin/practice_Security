package com.kyobo.lab;

/**
 * 3일차 · 03 (인가: 누가 무엇을 할 수 있나)
 *
 * [역할] "이 글의 작성자가 이 사람인가?"를 확인하는 소유자 검사기. @PreAuthorize 식 안에서 @postGuard로 부름
 *
 * [알아야 할 것]
 * - 빈 이름을 "postGuard"로 직접 정함. @PreAuthorize의 @postGuard와 철자가 같아야 함 (틀리면 500)
 * - URL에는 "누구의 글인지"가 없어서, DB에서 글을 꺼내 author와 비교해야만 판단 가능함 (가이드 1-1의 ③)
 * - 없는 글이면 false → 작성자가 아님으로 처리함 (관리자가 아니면 403)
 * - 화면에서 버튼을 숨기는 것과 상관없이 서버가 막음. API는 직접 부를 수 있기 때문 (가이드 1-6)
 */
public class PostGuard {
}
