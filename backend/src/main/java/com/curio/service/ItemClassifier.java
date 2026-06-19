package com.curio.service;

import com.curio.common.TextUtils;
import com.curio.dto.item.ClassificationResult;
import com.curio.entity.Item;
import com.curio.entity.Tag;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import com.curio.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 아이템에 AI 분류 결과(category + tags)를 채운다.
 * 신규 저장(ItemProcessor)과 기존 아이템 재분류(ItemService) 양쪽에서 재사용.
 * item 영속화는 호출자가 책임진다 (여기선 item 필드만 갱신).
 */
@Service
@RequiredArgsConstructor
public class ItemClassifier {

    /** 이 길이 미만의 텍스트는 분류 의미가 적어 AI 호출 없이 ETC 처리. */
    private static final int MIN_TEXT_LENGTH_FOR_AI = 20;

    private final GeminiService geminiService;
    private final TagRepository tagRepository;
    private final S3Service s3Service;

    public void classify(Item item) {
        // 이미지는 비전으로 분류한다(설계결정 #32). 여기(백필/재분류 경로)선 영구 보관본(S3)에서 바이트를 받는다.
        // 라이브 저장 경로는 ItemProcessor가 방금 다운로드한 바이트로 classifyImage를 직접 호출(재다운로드 회피).
        if (item.getType() == ItemType.IMAGE) {
            // 키 없으면 다운로드도 생략하고 미분류로 둔다(키 켠 뒤 백필이 잡게, #21).
            if (!geminiService.isEnabled()) {
                return;
            }
            S3Service.DownloadedImage img = s3Service.downloadFromKey(item.getS3Key());
            if (img != null) {
                classifyImage(item, img.bytes(), img.contentType());
            }
            return;
        }
        // 너무 짧은 텍스트는 비용만 들고 결과도 부정확해 ETC.
        if (item.getType() == ItemType.TEXT
                && (item.getContent() == null || item.getContent().length() < MIN_TEXT_LENGTH_FOR_AI)) {
            item.updateCategory(Category.ETC);
            return;
        }

        // 키가 없으면 분류를 미룬다 — category=null로 남겨, 키를 켠 뒤 reclassify-all이 채우게 한다.
        // (여기서 ETC로 굳히면 키를 켜도 백필이 못 잡아 영영 ETC에 갇힌다.)
        if (!geminiService.isEnabled()) {
            return;
        }

        // 호출 실패(null)면 category를 굳히지 않고 미분류(null)로 둔다 — 백필이 다시 잡게.
        // (ETC로 박으면 일시적 실패가 영구 오염되고, 백필도 진짜 기타와 구분 못 한다.)
        ClassificationResult result = geminiService.classify(item.getTitle(), item.getContent());
        if (result == null) {
            return;
        }
        item.updateCategory(result.category());
        for (String name : result.tags()) {
            item.addTag(getOrCreateTag(name));
        }
    }

    /**
     * 이미지 바이트로 비전 분류 — 제목(캡션)·category·tags를 한 번에 채운다(설계결정 #32).
     * 라이브 경로(ItemProcessor.processImage)가 방금 다운로드한 바이트로 직접 호출한다.
     * 실패/키없음/바이트없음이면 아무것도 굳히지 않고 폴백(날짜형 제목 + 미분류)을 그대로 둔다(#21·#30).
     */
    public void classifyImage(Item item, byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0 || !geminiService.isEnabled()) {
            return;
        }
        ClassificationResult result = geminiService.classifyImage(imageBytes, mimeType);
        if (result == null) {
            return;
        }
        item.updateCategory(result.category());
        for (String name : result.tags()) {
            item.addTag(getOrCreateTag(name));
        }
        // 사용자가 안 고친 제목만 캡션으로 채운다(가드는 Item.applyAiTitle 내부). 캡션 없으면 날짜형 폴백 유지.
        item.applyAiTitle(result.title());
    }

    private Tag getOrCreateTag(String rawName) {
        // 태그명도 컬럼 길이(Tag.NAME_MAX)를 넘으면 저장 시 잘리므로, 조회·생성 모두 잘린 값으로 통일해
        // findByName(원본)과 저장값(잘린값)이 어긋나 재조회가 빗나가는 일을 막는다.
        String name = TextUtils.truncate(rawName, Tag.NAME_MAX);
        // 빠른 경로: 이미 있으면 그대로 사용(잠금 없이).
        return tagRepository.findByName(name).orElseGet(() -> {
            // 없으면 원자적 insert(중복이어도 예외 없음) 후, 잠금 읽기로 (동시 생성분 포함) 확실히 확보한다.
            // 예전엔 saveAndFlush가 unique 충돌 시 DataIntegrityViolationException을 던져 본 트랜잭션을
            // rollback-only로 오염 → catch에서 복구한 듯 진행해도 커밋 때 500 + 저장 유실됐다.
            // INSERT IGNORE로 그 예외 경로를 없애고, REPEATABLE READ 스냅샷을 우회하는 잠금 읽기로 재조회한다.
            tagRepository.insertIgnore(name);
            return tagRepository.findByNameForUpdate(name)
                    .orElseThrow(() -> new IllegalStateException("태그 확보 실패: " + name));
        });
    }
}
