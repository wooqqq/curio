package com.curio.repository;

import com.curio.entity.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    List<Popup> findAllByOrderByCreatedAtDesc();

    /** 현재 활성 팝업(최대 1개 보장). 사용자 아카이브 진입 시 노출용. */
    Optional<Popup> findFirstByActiveTrueOrderByCreatedAtDesc();

    /** 활성 팝업 일괄 비활성화용 — 새 팝업을 활성화할 때 기존 것을 끄기 위해 조회. */
    List<Popup> findByActiveTrue();
}