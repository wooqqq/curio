package com.curio.processor;

import com.curio.dto.item.OgData;
import com.curio.entity.User;
import com.curio.repository.ItemRepository;
import com.curio.repository.UserRepository;
import com.curio.service.ItemClassifier;
import com.curio.service.OgCrawlerService;
import com.curio.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * ItemProcessor.process(봇 비동기 경로) 단위 테스트.
 * 봇 발화에 URL 앞뒤로 텍스트가 섞여도 URL만 추출해 저장해야 한다(웹 addLink와 동일).
 */
@ExtendWith(MockitoExtension.class)
class ItemProcessorProcessTest {

    @Mock UserRepository userRepository;
    @Mock ItemRepository itemRepository;
    @Mock OgCrawlerService ogCrawlerService;
    @Mock S3Service s3Service;
    @Mock ItemClassifier itemClassifier;
    @InjectMocks ItemProcessor processor;

    @Test
    void process_링크앞뒤에_텍스트가있어도_URL만_크롤한다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));
        given(itemRepository.existsByUserAndNormalizedUrl(any(), any())).willReturn(false);
        given(ogCrawlerService.crawl(any())).willReturn(new OgData("제목", null, null));

        processor.process(1L, "이거 꼭 봐 https://example.com/article 추천");

        verify(ogCrawlerService).crawl("https://example.com/article"); // 전체 발화가 아니라 URL만
    }

    @Test
    void process_이미지URL앞에_텍스트가있어도_URL만_업로드한다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));

        processor.process(1L, "짤 저장 https://example.com/a.png");

        verify(s3Service).uploadFromUrl(eq("https://example.com/a.png"), any());
    }
}
