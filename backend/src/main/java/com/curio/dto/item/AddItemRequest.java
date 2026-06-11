package com.curio.dto.item;

import jakarta.validation.constraints.NotBlank;

// 웹앱에서 링크 직접 추가 요청
public record AddItemRequest(
        @NotBlank(message = "링크를 입력해주세요.")
        String url
) {
}
