package com.curio.repository;

import com.curio.entity.Item;
import com.curio.entity.User;
import com.curio.entity.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByUserAndNormalizedUrl(User user, String normalizedUrl);

    Page<Item> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Item> findByUserIdAndCategoryOrderByCreatedAtDesc(Long userId, Category category, Pageable pageable);
}
