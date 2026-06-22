package com.curio.dto.item;

import com.curio.entity.Item;
import com.curio.entity.enums.Category;
import jakarta.validation.constraints.Size;

/**
 * 아이템 부분 수정 요청. 모든 필드 선택(null이면 해당 필드는 그대로 둔다).
 * - title: 제목 수정(null이면 변경 안 함, 공백만이면 거부). 수정하면 재크롤이 덮어쓰지 않게 표시된다.
 * - memo: 사용자 한 줄 메모(빈 문자열이면 메모 비우기).
 * - category: 카테고리 정정(null이면 변경 안 함). 수정하면 재분류가 덮어쓰지 않게 표시된다(설계결정 #33).
 * - content: 본문 편집. TEXT 타입에만 적용된다(LINK·IMAGE의 content는 OG/AI 파생이라 무시). 공백이면 거부(설계결정 #35).
 */
public record UpdateItemRequest(
        @Size(max = Item.TITLE_MAX, message = "제목이 너무 깁니다.")
        String title,

        @Size(max = Item.MEMO_MAX, message = "메모가 너무 깁니다.")
        String memo,

        Category category,

        @Size(max = 10000, message = "내용이 너무 깁니다.")
        String content
) {
}
