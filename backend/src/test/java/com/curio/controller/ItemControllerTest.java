package com.curio.controller;

import com.curio.config.CorsConfig;
import com.curio.config.SecurityConfig;
import com.curio.dto.item.ItemResponse;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.security.JwtUtil;
import com.curio.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ItemController 웹 슬라이스 테스트. 실제 SecurityConfig를 import해 인가 규칙을 그대로 태운다.
 * - 미인증 요청 거부
 * - 본인 소유가 아닌 아이템 접근 시 ITEM_NOT_FOUND(404) — 존재 여부 비노출(설계결정)
 * - 중복 링크 추가 시 DUPLICATE_URL(409), 입력 검증 실패 시 INVALID_INPUT(400)
 */
@WebMvcTest(ItemController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@ActiveProfiles("test")
class ItemControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ItemService itemService;
    @MockitoBean JwtUtil jwtUtil;                       // SecurityConfig가 JwtFilter 생성에 사용

    private static UsernamePasswordAuthenticationToken asUser(long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void 미인증_요청은_401() throws Exception {
        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_요청은_principal의_userId로_조회한다() throws Exception {
        given(itemService.getItems(eq(7L), any(), any(), eq(0), eq(20)))
                .willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/items").with(authentication(asUser(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(itemService).getItems(eq(7L), any(), any(), eq(0), eq(20));
    }

    @Test
    void 음수_page와_0_size는_안전하게_보정된다() throws Exception {
        // page<0 또는 size<1이면 PageRequest.of가 IllegalArgumentException을 던져 500이 되던 것을
        // 컨트롤러에서 미리 보정한다(page≥0, size≥1).
        given(itemService.getItems(eq(7L), any(), any(), eq(0), eq(1))).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/items?page=-1&size=0").with(authentication(asUser(7L))))
                .andExpect(status().isOk());

        verify(itemService).getItems(eq(7L), any(), any(), eq(0), eq(1));
    }

    @Test
    void size가_상한을_넘으면_상한으로_제한된다() throws Exception {
        // 상한이 없으면 ?size=100000 같은 값이 거대 쿼리를 유발 → 최대 100으로 제한한다.
        given(itemService.getItems(eq(7L), any(), any(), eq(0), eq(100))).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/items?size=100000").with(authentication(asUser(7L))))
                .andExpect(status().isOk());

        verify(itemService).getItems(eq(7L), any(), any(), eq(0), eq(100));
    }

    @Test
    void 남의_아이템_삭제는_404_ITEM_NOT_FOUND() throws Exception {
        // 본인 소유가 아니면 서비스가 ITEM_NOT_FOUND를 던진다(존재 여부 비노출).
        // delete는 void라 BDDMockito의 willThrow().given() 형태로 스텁한다.
        willThrow(new CurioException(ErrorCode.ITEM_NOT_FOUND))
                .given(itemService).delete(any(), any());

        mockMvc.perform(delete("/api/v1/items/99").with(authentication(asUser(7L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
    }

    @Test
    void 중복_링크_추가는_409_DUPLICATE_URL() throws Exception {
        given(itemService.addLink(eq(7L), any()))
                .willThrow(new CurioException(ErrorCode.DUPLICATE_URL));

        mockMvc.perform(post("/api/v1/items")
                        .with(authentication(asUser(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_URL"));
    }

    @Test
    void 빈_url_추가는_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .with(authentication(asUser(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 제목_메모_수정은_principal의_userId로_위임한다() throws Exception {
        given(itemService.update(eq(7L), eq(5L), any())).willReturn(null);

        mockMvc.perform(patch("/api/v1/items/5")
                        .with(authentication(asUser(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"내가 고친 제목\",\"memo\":\"나중에 읽기\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(itemService).update(eq(7L), eq(5L), any());
    }

    @Test
    void 남의_아이템_수정은_404_ITEM_NOT_FOUND() throws Exception {
        given(itemService.update(any(), any(), any()))
                .willThrow(new CurioException(ErrorCode.ITEM_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/items/99")
                        .with(authentication(asUser(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
    }

    @Test
    void 미인증_수정은_401() throws Exception {
        mockMvc.perform(patch("/api/v1/items/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
