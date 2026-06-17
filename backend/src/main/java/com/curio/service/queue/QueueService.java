package com.curio.service.queue;

import com.curio.entity.enums.ItemType;

public interface QueueService {
    /**
     * 봇 입력을 비동기 저장 큐에 넣는다.
     * @param type 카카오가 알려준 명시적 타입(IMAGE_UPLOAD 등). null이면 내용으로 자동 감지(detectType).
     */
    void enqueue(Long userId, String content, ItemType type);
}
