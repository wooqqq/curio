package com.curio.repository;

import com.curio.entity.Item;
import com.curio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByUserAndNormalizedUrl(User user, String normalizedUrl);
}
