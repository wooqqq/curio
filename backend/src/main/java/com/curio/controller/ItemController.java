package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.dto.item.AddItemRequest;
import com.curio.dto.item.ItemResponse;
import com.curio.dto.item.TagRequest;
import com.curio.dto.item.TextRequest;
import com.curio.dto.item.UpdateItemRequest;
import com.curio.entity.enums.Category;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    /** 페이지 크기 상한. 클라이언트가 큰 size를 넣어도 거대 쿼리가 안 나가게 막는다. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ItemService itemService;

    @GetMapping
    public ApiResponse<Page<ItemResponse>> getItems(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // page<0·size<1이면 PageRequest.of가 예외(→500)라, 받기 전에 안전 범위로 보정한다.
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.success(itemService.getItems(userId, category, q, safePage, safeSize));
    }

    /** 웹앱에서 링크 직접 추가 (봇 없이 저장). 성공 시 저장된 아이템, 중복이면 DUPLICATE_URL. */
    @PostMapping
    public ApiResponse<ItemResponse> add(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AddItemRequest request
    ) {
        return ApiResponse.success(itemService.addLink(userId, request.url()));
    }

    /** 웹앱에서 텍스트(메모) 직접 추가 (설계결정 #35). 성공 시 저장된 TEXT 아이템. */
    @PostMapping("/text")
    public ApiResponse<ItemResponse> addText(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TextRequest request
    ) {
        return ApiResponse.success(itemService.addText(userId, request.text()));
    }

    /** 웹앱에서 이미지 파일 직접 업로드 (설계결정 #35). 성공 시 비전 분류까지 채운 IMAGE 아이템. */
    @PostMapping("/image")
    public ApiResponse<ItemResponse> addImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new CurioException(ErrorCode.INVALID_IMAGE);
        }
        return ApiResponse.success(itemService.addImage(userId, file.getBytes()));
    }

    /** 아이템 제목/메모 수정. {title?, memo?} 부분 업데이트. 본인 소유 아니면 ITEM_NOT_FOUND. */
    @PatchMapping("/{id}")
    public ApiResponse<ItemResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        return ApiResponse.success(itemService.update(userId, id, request));
    }

    /** 아이템에 태그 추가(사용자 수동). 성공 시 갱신된 아이템. 본인 소유 아니면 ITEM_NOT_FOUND. */
    @PostMapping("/{id}/tags")
    public ApiResponse<ItemResponse> addTag(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request
    ) {
        return ApiResponse.success(itemService.addTag(userId, id, request.name()));
    }

    /** 아이템에서 태그 제거. 이름은 쿼리 파라미터(한글·공백은 URL 인코딩). 성공 시 갱신된 아이템. */
    @DeleteMapping("/{id}/tags")
    public ApiResponse<ItemResponse> removeTag(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam String name
    ) {
        return ApiResponse.success(itemService.removeTag(userId, id, name));
    }

    @PostMapping("/{id}/recrawl")
    public ApiResponse<ItemResponse> recrawl(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(itemService.recrawl(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        itemService.delete(userId, id);
        return ApiResponse.success();
    }

    /** 분류 안 된(category=null) 아이템 일괄 재분류. AI 키를 켠 뒤 한 번 호출하는 백필용. */
    @PostMapping("/reclassify-all")
    public ApiResponse<Map<String, Integer>> reclassifyAll(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("reclassified", itemService.reclassifyAll(userId)));
    }
}
