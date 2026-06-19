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
    @Mock S3Service s3Service;
    @InjectMocks ItemClassifier classifier;

    @Test
    void 이미지_백필은_S3에서_받아_비전으로_분류한다() { // 설계결정 #32 — reclassify-all 경로
        Item item = Item.builder().type(ItemType.IMAGE).s3Key("1/2026/06/x.jpg")
                .title("6월 19일 사진").build(); // 날짜형 기본 제목(사용자 미수정)
        given(geminiService.isEnabled()).willReturn(true);
        given(s3Service.downloadFromKey("1/2026/06/x.jpg"))
                .willReturn(new S3Service.DownloadedImage("1/2026/06/x.jpg", new byte[]{1, 2, 3}, "image/jpeg"));
        given(geminiService.classifyImage(any(), any()))
                .willReturn(new ClassificationResult(Category.DEVELOPMENT, List.of("다이어그램"), "스프링 필터 체인"));
        given(tagRepository.findByName(any())).willReturn(Optional.empty());
        given(tagRepository.findByNameForUpdate("다이어그램"))
                .willReturn(Optional.of(Tag.builder().name("다이어그램").build()));

        classifier.classify(item);

        assertThat(item.getCategory()).isEqualTo(Category.DEVELOPMENT);
        assertThat(item.getTitle()).isEqualTo("스프링 필터 체인"); // 캡션이 날짜형 제목을 덮어씀
        assertThat(item.getTags()).extracting(Tag::getName).containsExactly("다이어그램");
        verify(geminiService, never()).classify(any(), any()); // 텍스트 분류는 안 탐
    }

    @Test
    void 이미지_키없으면_S3다운로드없이_미분류로_남긴다() {
        Item item = Item.builder().type(ItemType.IMAGE).build(); // s3Key 없음
        given(geminiService.isEnabled()).willReturn(true);

        classifier.classify(item);

        assertThat(item.getCategory()).isNull();
        verify(geminiService, never()).classifyImage(any(), any());
    }

    @Test
    void 이미지_비전성공시_사용자가_고친제목은_덮어쓰지않는다() { // titleEditedByUser 가드(#31)를 캡션에도 적용
        Item item = Item.builder().type(ItemType.IMAGE).title("내가 고친 제목").build();
        item.editTitle("내가 고친 제목"); // titleEditedByUser=true
        given(geminiService.isEnabled()).willReturn(true);
        given(geminiService.classifyImage(any(), any()))
                .willReturn(new ClassificationResult(Category.ETC, List.of(), "AI 캡션"));

        classifier.classifyImage(item, new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(item.getTitle()).isEqualTo("내가 고친 제목"); // 캡션 무시
        assertThat(item.getCategory()).isEqualTo(Category.ETC); // 카테고리는 갱신
    }

    @Test
    void 이미지_비전실패시_카테고리_제목_그대로_둔다() { // 실패를 굳히지 않음(#21·#30)
        Item item = Item.builder().type(ItemType.IMAGE).title("6월 19일 사진").build();
        given(geminiService.isEnabled()).willReturn(true);
        given(geminiService.classifyImage(any(), any())).willReturn(null);

        classifier.classifyImage(item, new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(item.getCategory()).isNull();
        assertThat(item.getTitle()).isEqualTo("6월 19일 사진"); // 날짜형 폴백 유지
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
        // 신규 태그: 빠른 조회는 미스 → insertIgnore 후 잠금 읽기로 확보.
        given(tagRepository.findByName(any())).willReturn(Optional.empty());
        given(tagRepository.findByNameForUpdate("spring"))
                .willReturn(Optional.of(Tag.builder().name("spring").build()));
        given(tagRepository.findByNameForUpdate("jpa"))
                .willReturn(Optional.of(Tag.builder().name("jpa").build()));

        classifier.classify(item);

        assertThat(item.getCategory()).isEqualTo(Category.DEVELOPMENT);
        assertThat(item.getTags()).extracting(Tag::getName).containsExactly("spring", "jpa");
        // saveAndFlush(충돌 시 트랜잭션 오염) 대신 INSERT IGNORE 경로를 타야 한다.
        verify(tagRepository).insertIgnore("spring");
        verify(tagRepository).insertIgnore("jpa");
    }

    @Test
    void 긴_태그명은_50자로_자른_뒤_조회_생성한다() { // 예측 버그 #2 — 조회·저장 길이 불일치 방지
        Item item = linkItem();
        String longTag = "t".repeat(Tag.NAME_MAX + 20);
        String cut = "t".repeat(Tag.NAME_MAX);
        given(geminiService.isEnabled()).willReturn(true);
        given(geminiService.classify(any(), any()))
                .willReturn(new ClassificationResult(Category.DEVELOPMENT, List.of(longTag)));
        given(tagRepository.findByName(any())).willReturn(Optional.empty());
        given(tagRepository.findByNameForUpdate(cut))
                .willReturn(Optional.of(Tag.builder().name(cut).build()));

        classifier.classify(item);

        // 빠른 조회·insert·잠금 재조회 모두 잘린 이름(50자)으로 일관돼야 한다.
        verify(tagRepository).findByName(cut);
        verify(tagRepository).insertIgnore(cut);
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
