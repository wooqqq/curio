package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.dto.item.ItemResponse;
import com.curio.entity.enums.Category;
import com.curio.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public ApiResponse<Page<ItemResponse>> getItems(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(itemService.getItems(userId, category, q, page, size));
    }

    @PostMapping("/{id}/recrawl")
    public ApiResponse<ItemResponse> recrawl(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(itemService.recrawl(userId, id));
    }

    /** 분류 안 된(category=null) 아이템 일괄 재분류. AI 키를 켠 뒤 한 번 호출하는 백필용. */
    @PostMapping("/reclassify-all")
    public ApiResponse<Map<String, Integer>> reclassifyAll(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("reclassified", itemService.reclassifyAll(userId)));
    }
}
