package com.curio.processor;

import com.curio.dto.item.OgData;
import com.curio.entity.User;
import com.curio.entity.enums.ItemType;
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

        processor.process(1L, "이거 꼭 봐 https://example.com/article 추천", null); // 타입 미지정 → 자동 감지

        verify(ogCrawlerService).crawl("https://example.com/article"); // 전체 발화가 아니라 URL만
    }

    @Test
    void process_이미지URL앞에_텍스트가있어도_URL만_업로드한다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));

        processor.process(1L, "짤 저장 https://example.com/a.png", null); // 자동 감지로도 IMAGE

        verify(s3Service).uploadFromUrl(eq("https://example.com/a.png"), any());
    }

    @Test
    void process_타입이IMAGE로_명시되면_확장자없는URL도_업로드한다() { // 카카오 IMAGE_UPLOAD — 정규식 의존 X
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));

        // 확장자가 깔끔하지 않아 detectType이라면 LINK로 오인할 URL이라도, 명시 타입이면 이미지로 업로드한다.
        processor.process(1L, "https://talk.kakaocdn.net/dna/abc/i_hash?credential=x", ItemType.IMAGE);

        verify(s3Service).uploadFromUrl(eq("https://talk.kakaocdn.net/dna/abc/i_hash?credential=x"), any());
    }
}
