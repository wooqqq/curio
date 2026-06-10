package com.curio.service;

import com.curio.dto.popup.PopupRequest;
import com.curio.dto.popup.PopupResponse;
import com.curio.entity.Popup;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.repository.PopupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupService {

    private final PopupRepository popupRepository;

    /** 사용자 아카이브 진입 시 노출할 활성 팝업. 없으면 null. */
    public PopupResponse getActive() {
        return popupRepository.findFirstByActiveTrueOrderByCreatedAtDesc()
                .map(PopupResponse::from)
                .orElse(null);
    }

    /** 관리자 — 전체 팝업 목록(최신순). */
    public List<PopupResponse> list() {
        return popupRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PopupResponse::from)
                .toList();
    }

    @Transactional
    public PopupResponse create(PopupRequest request) {
        if (request.active()) {
            deactivateActive();
        }
        Popup saved = popupRepository.save(
                Popup.builder()
                        .title(request.title())
                        .content(request.content())
                        .imageUrl(request.imageUrl())
                        .linkUrl(request.linkUrl())
                        .active(request.active())
                        .build());
        return PopupResponse.from(saved);
    }

    @Transactional
    public PopupResponse update(Long id, PopupRequest request) {
        Popup popup = findOrThrow(id);
        // 이 팝업을 활성화하는 경우, 기존 활성 팝업(자기 자신 제외)을 먼저 끈다 → 항상 1개 유지.
        if (request.active()) {
            popupRepository.findByActiveTrue().stream()
                    .filter(p -> !p.getId().equals(id))
                    .forEach(Popup::deactivate);
        }
        popup.update(request.title(), request.content(), request.imageUrl(),
                request.linkUrl(), request.active());
        return PopupResponse.from(popup);
    }

    @Transactional
    public void delete(Long id) {
        Popup popup = findOrThrow(id);
        popupRepository.delete(popup);
    }

    private void deactivateActive() {
        popupRepository.findByActiveTrue().forEach(Popup::deactivate);
    }

    private Popup findOrThrow(Long id) {
        return popupRepository.findById(id)
                .orElseThrow(() -> new CurioException(ErrorCode.POPUP_NOT_FOUND));
    }
}
