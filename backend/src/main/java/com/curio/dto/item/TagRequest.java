package com.curio.dto.item;

import com.curio.entity.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 아이템에 태그를 추가하는 요청. */
public record TagRequest(
        @NotBlank(message = "태그를 입력해 주세요.")
        @Size(max = Tag.NAME_MAX, message = "태그가 너무 깁니다.")
        String name
) {
}
