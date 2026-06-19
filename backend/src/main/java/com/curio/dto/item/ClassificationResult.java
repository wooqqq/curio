package com.curio.dto.item;

import com.curio.entity.enums.Category;

import java.util.List;

/**
 * AI 분류 결과. {@code title}은 비전(이미지) 분류에서만 채워지는 한 줄 캡션이고,
 * 텍스트/링크 분류 경로에선 제목을 만들지 않아 null이다(설계결정 #32).
 */
public record ClassificationResult(Category category, List<String> tags, String title) {

    /** 제목 없는 텍스트/링크 분류용 — title은 null. */
    public ClassificationResult(Category category, List<String> tags) {
        this(category, tags, null);
    }
}
