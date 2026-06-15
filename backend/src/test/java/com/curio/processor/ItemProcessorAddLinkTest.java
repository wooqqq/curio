package com.curio.processor;

import com.curio.dto.item.OgData;
import com.curio.entity.User;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * ItemProcessor.addLink(웹 동기 추가) 예외 경로 단위 테스트 (리포지토리 목킹).
 * 봇 경로(process)는 중복을 조용히 skip하지만, 웹은 DUPLICATE_URL로 명시한다(설계결정 #17).
 */
@ExtendWith(MockitoExtension.class)
class ItemProcessorAddLinkTest {

    @Mock UserRepository userRepository;
    @Mock ItemRepository itemRepository;
    @Mock OgCrawlerService ogCrawlerService;
    @Mock S3Service s3Service;
    @Mock ItemClassifier itemClassifier;
    @InjectMocks ItemProcessor processor;

    @Test
    void addLink_중복URL이면_DUPLICATE_URL_예외() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));
        given(itemRepository.existsByUserAndNormalizedUrl(any(), any())).willReturn(true);

        assertThatThrownBy(() -> processor.addLink(1L, "https://example.com/a"))
                .isInstanceOf(CurioException.class)
                .extracting(e -> ((CurioException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_URL);
    }

    @Test
    void addLink_저장중_중복제약위반이면_DUPLICATE_URL로_매핑한다() {
        // exists 사전 체크는 통과했지만(=false), 그 사이 같은 URL이 동시 추가돼
        // insert에서 unique 제약(uk_user_normalized_url)이 터지는 TOCTOU 상황.
        // 500이 아니라 409 DUPLICATE_URL로 알려야 한다.
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));
        given(itemRepository.existsByUserAndNormalizedUrl(any(), any())).willReturn(false);
        given(ogCrawlerService.crawl(any())).willReturn(new OgData("제목", null, null));
        given(itemRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("uk_user_normalized_url"));

        assertThatThrownBy(() -> processor.addLink(1L, "https://example.com/a"))
                .isInstanceOf(CurioException.class)
                .extracting(e -> ((CurioException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_URL);
    }

    @Test
    void addLink_URL이_아니면_INVALID_INPUT_예외() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> processor.addLink(1L, "그냥 텍스트 메모"))
                .isInstanceOf(CurioException.class)
                .extracting(e -> ((CurioException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
