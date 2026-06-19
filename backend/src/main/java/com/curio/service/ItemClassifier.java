package com.curio.service;

import com.curio.dto.item.ClassificationResult;
import com.curio.entity.Item;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
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
    private final TagService tagService;
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
            item.addTag(tagService.getOrCreate(name));
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
            item.addTag(tagService.getOrCreate(name));
        }
        // 사용자가 안 고친 제목만 캡션으로 채운다(가드는 Item.applyAiTitle 내부). 캡션 없으면 날짜형 폴백 유지.
        item.applyAiTitle(result.title());
    }
}
