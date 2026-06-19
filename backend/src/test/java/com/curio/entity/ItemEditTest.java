package com.curio.entity;

import com.curio.entity.enums.ItemType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 편집(제목/메모) 관련 엔티티 동작.
 * - 제목을 직접 고치면 재크롤(updateLinkMetadata)이 덮어쓰지 않는다(설계: 수정분 보호).
 * - 메모 빈 문자열은 null로 비운다.
 */
class ItemEditTest {

    private Item linkItem() {
        return Item.builder()
                .type(ItemType.LINK)
                .title("원래 제목")
                .originalUrl("https://example.com")
                .build();
    }

    @Test
    void 사용자가_고친_제목은_재크롤이_덮어쓰지_않는다() {
        Item item = linkItem();
        item.editTitle("내가 고친 제목");

        // 재크롤이 새 제목을 들고와도 사용자 제목 유지, content·썸네일은 갱신
        item.updateLinkMetadata("크롤링이 가져온 제목", "새 본문", "https://img");

        assertThat(item.getTitle()).isEqualTo("내가 고친 제목");
        assertThat(item.isTitleEditedByUser()).isTrue();
        assertThat(item.getContent()).isEqualTo("새 본문");
        assertThat(item.getThumbnailUrl()).isEqualTo("https://img");
    }

    @Test
    void 사용자가_안_고친_제목은_재크롤이_갱신한다() {
        Item item = linkItem();

        item.updateLinkMetadata("크롤링 제목", "본문", "https://img");

        assertThat(item.getTitle()).isEqualTo("크롤링 제목");
    }

    @Test
    void 제목은_길면_잘려서_저장된다() {
        Item item = linkItem();
        item.editTitle("가".repeat(Item.TITLE_MAX + 50));

        assertThat(item.getTitle()).hasSize(Item.TITLE_MAX);
    }

    @Test
    void 메모_빈문자열은_null로_비운다() {
        Item item = linkItem();
        item.updateMemo("메모 내용");
        assertThat(item.getMemo()).isEqualTo("메모 내용");

        item.updateMemo("   ");
        assertThat(item.getMemo()).isNull();
    }

    @Test
    void 메모는_길면_잘려서_저장된다() {
        Item item = linkItem();
        item.updateMemo("메".repeat(Item.MEMO_MAX + 50));

        assertThat(item.getMemo()).hasSize(Item.MEMO_MAX);
    }

    @Test
    void TEXT는_본문편집이_적용된다() { // 설계결정 #35 — content는 사용자 소유라 TEXT만 편집 1급
        Item item = Item.builder().type(ItemType.TEXT).title("t").content("옛 본문").build();

        item.updateContent("새 본문");

        assertThat(item.getContent()).isEqualTo("새 본문");
    }

    @Test
    void LINK은_본문편집이_무시된다() { // LINK content는 OG/AI 파생이라 사용자 편집 불가
        Item item = linkItem(); // content 없음(null)

        item.updateContent("덮어쓰기 시도");

        assertThat(item.getContent()).isNull();
    }
}
