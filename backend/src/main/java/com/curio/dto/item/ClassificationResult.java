package com.curio.dto.item;

import com.curio.entity.enums.Category;

import java.util.List;

public record ClassificationResult(Category category, List<String> tags) {
}
