package com.curio.dto.popup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 팝업 생성/수정 요청. imageUrl은 관리자 이미지 업로드(POST /admin/upload-image)로 받은 URL,
 * content는 이미지 대신 텍스트로 노출할 때 사용. 둘 중 하나는 채우는 게 보통이나 강제하진 않는다.
 */
public record PopupRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이내여야 합니다.")
        String title,

        String content,

        @Size(max = 1000, message = "이미지 URL이 너무 깁니다.")
        String imageUrl,

        @Size(max = 2000, message = "링크 URL이 너무 깁니다.")
        String linkUrl,

        boolean active
) {
}
