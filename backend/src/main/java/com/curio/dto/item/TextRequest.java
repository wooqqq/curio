package com.curio.dto.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 웹앱에서 텍스트(메모)를 직접 추가하는 요청 (설계결정 #35). */
public record TextRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 10000, message = "내용이 너무 깁니다.")
        String text
) {
}
