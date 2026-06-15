package com.curio.repository;

import com.curio.entity.Item;
import com.curio.entity.Tag;
import com.curio.entity.User;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ItemRepository.search 슬라이스 테스트 (H2, MySQL 호환 모드).
 * 카테고리 필터 + 키워드 LIKE(제목/본문/태그명) + 유저 격리 + DISTINCT + 최신순 정렬을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemRepositorySearchTest {

    @Autowired ItemRepository itemRepository;
    @Autowired TestEntityManager em;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = em.persist(User.builder().kakaoId("A").nickname("a").build());
        userB = em.persist(User.builder().kakaoId("B").nickname("b").build());
    }

    private Item link(User user, String title, String content, Category category, String... tagNames) {
        Item item = Item.builder()
                .user(user)
                .type(ItemType.LINK)
                .title(title)
                .content(content)
                .originalUrl("https://example.com/" + title)
                .normalizedUrl("example.com/" + title)
                .category(category)
                .build();
        for (String name : tagNames) {
            item.addTag(Tag.builder().name(name).build());
        }
        return em.persist(item);
    }

    private Page<Item> search(Long userId, Category category, String q) {
        return itemRepository.search(userId, category, q, PageRequest.of(0, 20));
    }

    @Test
    void 키워드가_제목에_매칭된다() {
        link(userA, "스프링 부트 입문", "본문", Category.DEVELOPMENT);
        link(userA, "리액트 훅", "본문", Category.DEVELOPMENT);
        em.flush();

        Page<Item> result = search(userA.getId(), null, "스프링");

        assertThat(result.getContent()).extracting(Item::getTitle).containsExactly("스프링 부트 입문");
    }

    @Test
    void 키워드가_본문에_매칭된다() {
        link(userA, "제목1", "코틀린 코루틴 정리", Category.DEVELOPMENT);
        link(userA, "제목2", "자바 스트림", Category.DEVELOPMENT);
        em.flush();

        Page<Item> result = search(userA.getId(), null, "코루틴");

        assertThat(result.getContent()).extracting(Item::getTitle).containsExactly("제목1");
    }

    @Test
    void 키워드가_태그명에_매칭된다() {
        link(userA, "면접 후기", "본문", Category.CAREER, "면접", "회고");
        link(userA, "딴 글", "본문", Category.CAREER, "잡담");
        em.flush();

        Page<Item> result = search(userA.getId(), null, "회고");

        assertThat(result.getContent()).extracting(Item::getTitle).containsExactly("면접 후기");
    }

    @Test
    void 키워드_검색은_대소문자를_무시한다() {
        link(userA, "Spring Boot Guide", "본문", Category.DEVELOPMENT);
        em.flush();

        Page<Item> result = search(userA.getId(), null, "spring");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void 카테고리로_필터링한다() {
        link(userA, "개발 글", "본문", Category.DEVELOPMENT);
        link(userA, "커리어 글", "본문", Category.CAREER);
        em.flush();

        Page<Item> result = search(userA.getId(), Category.CAREER, null);

        assertThat(result.getContent()).extracting(Item::getTitle).containsExactly("커리어 글");
    }

    @Test
    void q가_null이면_유저의_전체_아이템을_반환한다() {
        link(userA, "글1", "본문", Category.DEVELOPMENT);
        link(userA, "글2", "본문", Category.CAREER);
        link(userA, "글3", "본문", null);
        em.flush();

        Page<Item> result = search(userA.getId(), null, null);

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 다른_유저의_아이템은_조회되지_않는다() {
        link(userA, "내 글", "본문", Category.DEVELOPMENT);
        link(userB, "남의 글", "본문", Category.DEVELOPMENT);
        em.flush();

        Page<Item> result = search(userA.getId(), null, null);

        assertThat(result.getContent()).extracting(Item::getTitle).containsExactly("내 글");
    }

    @Test
    void 여러_태그가_키워드에_매칭돼도_DISTINCT로_중복되지_않는다() {
        // "스프링"이 두 태그에 모두 들어가 LEFT JOIN으로 행이 둘 생기지만, 아이템은 하나여야 한다.
        link(userA, "스프링 글", "본문", Category.DEVELOPMENT, "스프링부트", "스프링시큐리티");
        em.flush();

        Page<Item> result = search(userA.getId(), null, "스프링");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void 결과는_생성일_내림차순으로_정렬된다() {
        link(userA, "먼저", "본문", Category.DEVELOPMENT);
        em.flush();
        link(userA, "나중", "본문", Category.DEVELOPMENT);
        em.flush();

        Page<Item> result = search(userA.getId(), null, null);

        assertThat(result.getContent())
                .isSortedAccordingTo(Comparator.comparing(Item::getCreatedAt).reversed());
    }

    @Test
    void 결과가_페이지_크기를_넘으면_여러_페이지로_나뉜다() {
        for (int i = 0; i < 25; i++) {
            link(userA, "글" + i, "본문", Category.DEVELOPMENT);
        }
        em.flush();

        Page<Item> first = itemRepository.search(userA.getId(), null, null, PageRequest.of(0, 20));
        assertThat(first.getContent()).hasSize(20);
        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(first.getTotalPages()).isEqualTo(2);

        Page<Item> second = itemRepository.search(userA.getId(), null, null, PageRequest.of(1, 20));
        assertThat(second.getContent()).hasSize(5);
    }

    @Test
    void 태그가_여러개여도_총개수가_부풀지_않는다() {
        // LEFT JOIN i.tags로 아이템당 행이 태그 수만큼 생기지만 COUNT(DISTINCT i)라
        // totalElements는 태그 행(5)이 아니라 아이템 수(2)여야 한다. 페이지네이션이 깨지던 지점.
        link(userA, "글A", "본문", Category.DEVELOPMENT, "t1", "t2", "t3");
        link(userA, "글B", "본문", Category.DEVELOPMENT, "t4", "t5");
        em.flush();

        Page<Item> result = itemRepository.search(userA.getId(), null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }
}