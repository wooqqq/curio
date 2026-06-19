package com.curio.service;

import com.curio.common.TextUtils;
import com.curio.entity.Tag;
import com.curio.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 태그 조회/생성 단일 창구. race-safe get-or-create(설계결정 #28)를
 * AI 자동 분류(ItemClassifier)와 사용자 수동 태그 추가(ItemService) 양쪽에서 공유한다(#33).
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    /**
     * 이름으로 태그를 조회하고 없으면 생성한다(동시 생성에도 안전, #28).
     * 이름은 컬럼 길이(Tag.NAME_MAX)를 넘으면 저장 시 잘리므로, 조회·생성·재조회 모두 잘린 값으로 통일해
     * findByName(원본)과 저장값(잘린값)이 어긋나 재조회가 빗나가는 일을 막는다.
     */
    public Tag getOrCreate(String rawName) {
        String name = TextUtils.truncate(rawName, Tag.NAME_MAX);
        // 빠른 경로: 이미 있으면 그대로(잠금 없이).
        return tagRepository.findByName(name).orElseGet(() -> {
            // 없으면 원자적 insert(중복이어도 예외 없음) 후, 잠금 읽기로 (동시 생성분 포함) 확실히 확보한다.
            tagRepository.insertIgnore(name);
            return tagRepository.findByNameForUpdate(name)
                    .orElseThrow(() -> new IllegalStateException("태그 확보 실패: " + name));
        });
    }
}
