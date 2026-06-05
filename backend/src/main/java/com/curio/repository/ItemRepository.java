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

    /** 아직 분류되지 않은(category=null) 유저 아이템 — 재분류 백필용. */
    List<Item> findByUserIdAndCategoryIsNull(Long userId);

    /**
     * 사용자 아이템 조회 (카테고리 필터 + 키워드 검색).
     * category, q 둘 다 null이면 전체 조회. q는 제목/본문/AI요약/태그명을 대소문자 무시 LIKE 검색.
     */
    @Query(value = """
            SELECT DISTINCT i FROM Item i
            LEFT JOIN i.tags t
            WHERE i.user.id = :userId
              AND (:category IS NULL OR i.category = :category)
              AND (:q IS NULL
                   OR LOWER(i.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.content) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.aiSummary) LIKE LOWER(CONCAT('%', :q, '%'))
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
                   OR LOWER(i.aiSummary) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Item> search(@Param("userId") Long userId,
                       @Param("category") Category category,
                       @Param("q") String q,
                       Pageable pageable);
}
