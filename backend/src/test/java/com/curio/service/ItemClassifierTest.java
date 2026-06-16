package com.curio.service;

import com.curio.dto.item.ClassificationResult;
import com.curio.entity.Item;
import com.curio.entity.Tag;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import com.curio.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ItemClassifier 단위 테스트 (GeminiService·TagRepository 목킹).
 * 핵심: AI 호출 실패를 ETC로 굳히지 않고 미분류(null)로 남기는 동작(설계결정 #21) 회귀 방어.
 */
@ExtendWith(MockitoExtension.class)
class ItemClassifierTest {

    @Mock GeminiService geminiService;
    @Mock TagRepository tagRepository;
    @InjectMocks ItemClassifier classifier;

    @Test
    void 이미지는_AI호출없이_ETC로_분류한다() {
        Item item = Item.builder().type(ItemType.IMAGE).build();

        classifier.classify(item);

        assertThat(item.getCategory()).isEqualTo(Category.ETC);
        verify(geminiService, never()).classify(any(), any());
    }

    @Test
    void 너무_짧은_텍스트는_AI호출없이_ETC로_분류한다() {
        Item item = Item.builder().type(ItemType.TEXT).content("짧은 메모").build();

        classifier.classify(item);

        assertThat(item.getCategory()).isEqualTo(Category.ETC);
        verify(geminiService, never()).classify(any(), any());
    }

    @Test
    void 키가_비활성이면_미분류로_남긴다() {
        Item item = linkItem();
        given(geminiService.isEnabled()).willReturn(false);

        classifier.classify(item);

        assertThat(item.getCategory()).isNull();
        verify(geminiService, never()).classify(any(), any());
    }

    @Test
    void AI호출이_실패하면_ETC가아니라_미분류로_남긴다() { // 설계결정 #21 회귀 방어
        Item item = linkItem();
        given(geminiService.isEnabled()).willReturn(true);
        given(geminiService.classify(any(), any())).willReturn(null);

        classifier.classify(item);

        assertThat(item.getCategory()).isNull();
    }

    @Test
    void 분류성공시_카테고리와_태그를_채운다() {
        Item item = linkItem();
        given(geminiService.isEnabled()).willReturn(true);
        given(geminiService.classify(any(), any()))
                .willReturn(new ClassificationResult(Category.DEVELOPMENT, List.of("spring", "jpa")));
        given(tagRepository.findByName(any())).willReturn(Optional.empty());
        given(tagRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        classifier.classify(item);

        assertThat(item.getCategory()).isEqualTo(Category.DEVELOPMENT);
        assertThat(item.getTags()).extracting(Tag::getName).containsExactly("spring", "jpa");
    }

    @Test
    void 긴_태그명은_50자로_자른_뒤_조회_생성한다() { // 예측 버그 #2 — 조회·저장 길이 불일치 방지
        Item item = linkItem();
        String longTag = "t".repeat(Tag.NAME_MAX + 20);
        given(geminiService.isEnabled()).willReturn(true);
        given(geminiService.classify(any(), any()))
                .willReturn(new ClassificationResult(Category.DEVELOPMENT, List.of(longTag)));
        given(tagRepository.findByName(any())).willReturn(Optional.empty());
        given(tagRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        classifier.classify(item);

        // 조회는 잘린 이름으로, 저장된 태그도 50자.
        verify(tagRepository).findByName("t".repeat(Tag.NAME_MAX));
        assertThat(item.getTags()).singleElement()
                .extracting(Tag::getName, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .hasSize(Tag.NAME_MAX);
    }

    private Item linkItem() {
        return Item.builder()
                .type(ItemType.LINK)
                .title("제목")
                .content("분류에 충분한 길이의 본문입니다.")
                .build();
    }
}
