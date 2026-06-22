package com.curio.service;

import com.curio.dto.item.UpdateItemRequest;
import com.curio.entity.Item;
import com.curio.entity.User;
import com.curio.entity.enums.ItemType;
import com.curio.exception.CurioException;
import com.curio.processor.ItemProcessor;
import com.curio.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * ItemService.update의 본문(content) 편집 가드 (설계결정 #35).
 * content는 사용자 소유인 TEXT에만 적용 — LINK·IMAGE의 content는 OG/AI 파생이라 무시. 공백이면 거부.
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock OgCrawlerService ogCrawlerService;
    @Mock ItemClassifier itemClassifier;
    @Mock ItemProcessor itemProcessor;
    @Mock TagService tagService;
    @InjectMocks ItemService itemService;

    private Item owned(long ownerId, ItemType type, String content) {
        User user = mock(User.class);
        given(user.getId()).willReturn(ownerId);
        return Item.builder().user(user).type(type).title("t").content(content).build();
    }

    private UpdateItemRequest contentOnly(String content) {
        return new UpdateItemRequest(null, null, null, content);
    }

    @Test
    void TEXT_본문편집은_적용된다() {
        Item item = owned(7L, ItemType.TEXT, "옛 본문");
        given(itemRepository.findById(5L)).willReturn(Optional.of(item));

        itemService.update(7L, 5L, contentOnly("새 본문"));

        assertThat(item.getContent()).isEqualTo("새 본문");
    }

    @Test
    void LINK_본문편집은_무시된다() { // content는 OG/AI 파생이라 TEXT만 편집 허용
        Item item = owned(7L, ItemType.LINK, "OG 본문");
        given(itemRepository.findById(5L)).willReturn(Optional.of(item));

        itemService.update(7L, 5L, contentOnly("덮어쓰기 시도"));

        assertThat(item.getContent()).isEqualTo("OG 본문");
    }

    @Test
    void TEXT_본문을_공백으로_비우면_INVALID_INPUT() {
        Item item = owned(7L, ItemType.TEXT, "옛 본문");
        given(itemRepository.findById(5L)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.update(7L, 5L, contentOnly("   ")))
                .isInstanceOf(CurioException.class);
    }

    @Test
    void 남의_아이템_수정은_ITEM_NOT_FOUND() { // 본인 소유 아니면 존재 비노출
        Item item = owned(99L, ItemType.TEXT, "본문");
        given(itemRepository.findById(5L)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.update(7L, 5L, contentOnly("x")))
                .isInstanceOf(CurioException.class);
    }
}
