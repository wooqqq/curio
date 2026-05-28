package com.curio.service.queue;

public interface QueueService {
    void enqueue(Long userId, String utterance);
}
