package com.curio.entity;

import com.curio.entity.enums.ItemType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엔티티가 컬럼 길이를 넘는 값을 저장 전 잘라내는지 검증(예측 버그 #2).
 * 크롤 제목·URL·태그명이 길이를 초과하면 MySQL strict 모드에서 insert가 통째로 롤백(500)되므로,
 * 빌더/갱신/태그 생성자가 길이를 맞춰야 한다.
 */
class ItemLengthTest {

    @Test
    void 빌더가_제목과_URL을_컬럼길이로_자른다() {
        Item item = Item.builder()
                .type(ItemType.LINK)
                .title("a".repeat(Item.TITLE_MAX + 100))
                .thumbnailUrl("b".repeat(Item.THUMBNAIL_URL_MAX + 100))
                .originalUrl("c".repeat(Item.URL_MAX + 100))
                .normalizedUrl("d".repeat(Item.URL_MAX + 100))
                .s3Key("e".repeat(Item.S3_KEY_MAX + 100))
                .build();

        assertThat(item.getTitle()).hasSize(Item.TITLE_MAX);
        assertThat(item.getThumbnailUrl()).hasSize(Item.THUMBNAIL_URL_MAX);
        assertThat(item.getOriginalUrl()).hasSize(Item.URL_MAX);
        assertThat(item.getNormalizedUrl()).hasSize(Item.URL_MAX);
        assertThat(item.getS3Key()).hasSize(Item.S3_KEY_MAX);
    }

    @Test
    void 짧은_값은_그대로_둔다() {
        Item item = Item.builder()
                .type(ItemType.LINK)
                .title("짧은 제목")
                .originalUrl("https://example.com")
                .build();

        assertThat(item.getTitle()).isEqualTo("짧은 제목");
        assertThat(item.getOriginalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void 재크롤_갱신도_제목을_자른다() {
        Item item = Item.builder().type(ItemType.LINK).title("원본").build();

        item.updateLinkMetadata("z".repeat(Item.TITLE_MAX + 100), "본문", "thumb");

        assertThat(item.getTitle()).hasSize(Item.TITLE_MAX);
    }

    @Test
    void 태그명이_길면_50자로_자른다() {
        Tag tag = Tag.builder().name("t".repeat(Tag.NAME_MAX + 30)).build();

        assertThat(tag.getName()).hasSize(Tag.NAME_MAX);
    }
}
