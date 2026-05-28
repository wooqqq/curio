package com.curio.service.queue;

import com.curio.processor.ItemProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AsyncQueueService implements QueueService {

    private final ItemProcessor itemProcessor;

    @Override
    @Async("itemProcessorExecutor")
    public void enqueue(Long userId, String utterance) {
        itemProcessor.process(userId, utterance);
    }
}
