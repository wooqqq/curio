package com.curio.repository;

import com.curio.entity.Item;
import com.curio.entity.User;
import com.curio.entity.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByUserAndNormalizedUrl(User user, String normalizedUrl);

    /**
     * 재분류 대상 — 미분류(category=null)이거나 ETC인 유저 아이템.
     * ETC를 포함하는 이유: AI 호출 실패 시 ETC로 폴백되므로, 키/모델을 고친 뒤
     * 잘못 ETC로 박힌 것까지 백필해야 한다. (정상 ETC도 다시 한 번 분류 시도 — 무해)
     * 단 사용자가 카테고리를 직접 고친 것(categoryEditedByUser)은 제외 — 정정을 백필이 되돌리지 않게(#33).
     */
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.categoryEditedByUser = false "
            + "AND (i.category IS NULL OR i.category = :etc)")
    List<Item> findForReclassify(@Param("userId") Long userId, @Param("etc") Category etc);

    /**
     * 사용자 아이템 조회 (카테고리 필터 + 키워드 검색).
     * category, q 둘 다 null이면 전체 조회. q는 제목/본문/태그명을 대소문자 무시 LIKE 검색.
     */
    @Query(value = """
            SELECT DISTINCT i FROM Item i
            LEFT JOIN i.tags t
            WHERE i.user.id = :userId
              AND (:category IS NULL OR i.category = :category)
              AND (:q IS NULL
                   OR LOWER(i.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.content) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY i.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT i) FROM Item i
            LEFT JOIN i.tags t
            WHERE i.user.id = :userId
              AND (:category IS NULL OR i.category = :category)
              AND (:q IS NULL
                   OR LOWER(i.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.content) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Item> search(@Param("userId") Long userId,
                       @Param("category") Category category,
                       @Param("q") String q,
                       Pageable pageable);
}
