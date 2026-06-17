package com.curio.controller;

import com.curio.dto.kakao.KakaoSkillRequest;
import com.curio.entity.User;
import com.curio.entity.enums.ItemType;
import com.curio.repository.UserRepository;
import com.curio.service.LinkCodeService;
import com.curio.service.queue.QueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * KakaoController 단위 테스트 — 실제 오픈빌더 페이로드(JSON)를 그대로 역직렬화해 라우팅을 검증한다.
 * 사진 전송 시 params.media(type=image,url) + flow.trigger=IMAGE_UPLOAD로 오는 걸 IMAGE로 명시 처리(설계결정 #30).
 */
@ExtendWith(MockitoExtension.class)
class KakaoControllerTest {

    private static final String BOT_KEY = "e6e77b8a85a728726d13fa0b3512331c0cf34c30513e9fedf5e34895c41c1f04ae";
    private static final String IMAGE_URL =
            "https://talk.kakaocdn.net/dna/biDWLe/dJMcbfrr938/QPvD4hfEMVh9vcQ14EKASk/i_7bdf57c4b1b7.png?credential=zf3biCPbmWRjbqf40YGePFLewdou7TIK&expires=1876290921&signature=abc%3D";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock UserRepository userRepository;
    @Mock LinkCodeService linkCodeService;
    @Mock QueueService queueService;
    @InjectMocks KakaoController controller;

    @Test
    void 사진_업로드_페이로드는_media_url을_IMAGE로_큐에_넣는다() throws Exception {
        String json = """
                {
                  "userRequest": {
                    "user": { "id": "%s", "type": "botUserKey" },
                    "utterance": "%s",
                    "params": { "surface": "Kakaotalk.plusfriend", "media": { "type": "image", "url": "%s" } }
                  },
                  "flow": { "trigger": { "type": "IMAGE_UPLOAD" } }
                }
                """.formatted(BOT_KEY, IMAGE_URL, IMAGE_URL);
        givenLinkedUser(1L);

        controller.skill(objectMapper.readValue(json, KakaoSkillRequest.class));

        // utterance가 아니라 명시 media.url을, 타입 IMAGE로 큐에 넣어야 한다.
        verify(queueService).enqueue(eq(1L), eq(IMAGE_URL), eq(ItemType.IMAGE));
    }

    @Test
    void 텍스트_발화는_타입미지정으로_큐에_넣는다() throws Exception {
        String json = """
                {
                  "userRequest": {
                    "user": { "id": "%s", "type": "botUserKey" },
                    "utterance": "이건 그냥 메모"
                  }
                }
                """.formatted(BOT_KEY);
        givenLinkedUser(1L);

        controller.skill(objectMapper.readValue(json, KakaoSkillRequest.class));

        verify(queueService).enqueue(eq(1L), eq("이건 그냥 메모"), isNull());
    }

    private void givenLinkedUser(Long userId) {
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(userRepository.findByBotUserKey(BOT_KEY)).willReturn(Optional.of(user));
    }
}
