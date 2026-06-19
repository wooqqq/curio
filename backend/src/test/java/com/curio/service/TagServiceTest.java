package com.curio.service;

import com.curio.entity.Tag;
import com.curio.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TagService.getOrCreate 단위 테스트.
 * 핵심: 빠른 조회(히트)는 잠금/insert 없이 / 미스는 INSERT IGNORE + 잠금 읽기로 race-safe 확보(설계결정 #28),
 * 그리고 이름은 NAME_MAX로 잘라 조회·생성·재조회를 일관시킨다(예측버그 #2).
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepository;
    @InjectMocks TagService tagService;

    @Test
    void 이미있으면_잠금없이_그대로_반환한다() {
        Tag existing = Tag.builder().name("spring").build();
        given(tagRepository.findByName("spring")).willReturn(Optional.of(existing));

        Tag result = tagService.getOrCreate("spring");

        assertThat(result).isSameAs(existing);
        verify(tagRepository, never()).insertIgnore("spring");
        verify(tagRepository, never()).findByNameForUpdate("spring");
    }

    @Test
    void 없으면_INSERT_IGNORE_후_잠금읽기로_확보한다() { // #28 — 동시 생성에도 안전
        given(tagRepository.findByName("jpa")).willReturn(Optional.empty());
        given(tagRepository.findByNameForUpdate("jpa"))
                .willReturn(Optional.of(Tag.builder().name("jpa").build()));

        Tag result = tagService.getOrCreate("jpa");

        assertThat(result.getName()).isEqualTo("jpa");
        verify(tagRepository).insertIgnore("jpa");
        verify(tagRepository).findByNameForUpdate("jpa");
    }

    @Test
    void 긴_이름은_50자로_자른_뒤_조회_생성한다() { // 예측 버그 #2 — 조회·저장 길이 불일치 방지
        String longName = "t".repeat(Tag.NAME_MAX + 20);
        String cut = "t".repeat(Tag.NAME_MAX);
        given(tagRepository.findByName(cut)).willReturn(Optional.empty());
        given(tagRepository.findByNameForUpdate(cut))
                .willReturn(Optional.of(Tag.builder().name(cut).build()));

        Tag result = tagService.getOrCreate(longName);

        // 빠른 조회·insert·잠금 재조회 모두 잘린 이름(50자)으로 일관돼야 한다.
        verify(tagRepository).findByName(cut);
        verify(tagRepository).insertIgnore(cut);
        assertThat(result.getName()).hasSize(Tag.NAME_MAX);
    }
}
