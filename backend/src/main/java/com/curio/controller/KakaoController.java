package com.curio.controller;

import com.curio.dto.kakao.KakaoSkillRequest;
import com.curio.dto.kakao.KakaoSkillResponse;
import com.curio.entity.User;
import com.curio.entity.enums.ItemType;
import com.curio.repository.UserRepository;
import com.curio.service.LinkCodeService;
import com.curio.service.queue.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/kakao")
@RequiredArgsConstructor
public class KakaoController {

    private static final Pattern LINK_CODE_PATTERN = Pattern.compile("^[A-Z2-9]{6}$");

    private final UserRepository userRepository;
    private final LinkCodeService linkCodeService;
    private final QueueService queueService;

    @PostMapping("/skill")
    public KakaoSkillResponse skill(@RequestBody KakaoSkillRequest request) {
        String botUserKey = extractBotUserKey(request);
        if (!StringUtils.hasText(botUserKey)) {
            return KakaoSkillResponse.text("요청을 처리할 수 없어요. 다시 시도해주세요.");
        }

        // 사진 업로드면 명시적 media.url을 쓴다 — utterance 정규식 매칭 우연에 기대지 않는다(설계결정 #30).
        Optional<String> imageUrl = extractUploadedImageUrl(request);
        String utterance = extractUtterance(request);

        if (imageUrl.isEmpty() && !StringUtils.hasText(utterance)) {
            return KakaoSkillResponse.text("요청을 처리할 수 없어요. 다시 시도해주세요.");
        }

        log.debug("Kakao skill request: botUserKey={} image={} utterance={}", botUserKey, imageUrl.isPresent(), utterance);

        Optional<User> userOpt = userRepository.findByBotUserKey(botUserKey);

        // 연동 코드 입력 처리 (텍스트일 때만 — 이미지는 코드가 아니다)
        if (imageUrl.isEmpty()) {
            String upperUtterance = utterance.trim().toUpperCase();
            if (LINK_CODE_PATTERN.matcher(upperUtterance).matches()) {
                return handleLinkCode(botUserKey, upperUtterance, userOpt);
            }
        }

        // 미연동 유저
        if (userOpt.isEmpty()) {
            return KakaoSkillResponse.text(
                    "안녕하세요! 큐리오 봇이에요 📚\n\n" +
                    "먼저 큐리오 웹앱에서 계정 연동이 필요해요.\n" +
                    "웹앱 로그인 후 '연동 코드 받기'를 눌러 코드 6자리를 여기에 입력해주세요."
            );
        }

        // 연동된 유저 → 아이템 저장. 이미지는 IMAGE로 명시, 그 외는 null로 자동 감지(텍스트/링크).
        Long userId = userOpt.get().getId();
        if (imageUrl.isPresent()) {
            queueService.enqueue(userId, imageUrl.get(), ItemType.IMAGE);
        } else {
            queueService.enqueue(userId, utterance.trim(), null);
        }

        return KakaoSkillResponse.text("📌 저장 중이에요!\n잠시 후 아카이브에서 확인하세요.");
    }

    /**
     * 사진 업로드면 이미지 URL을 돌려준다. 카카오는 사진 전송 시 userRequest.params.media(type=image, url)에
     * 이미지 URL을 명시로 담고 flow.trigger.type=IMAGE_UPLOAD를 준다(실측, 설계결정 #30). URL을 직접 들고 있는
     * media를 기준으로 판별한다 — utterance가 비거나 형식이 달라도 안전하다.
     */
    private Optional<String> extractUploadedImageUrl(KakaoSkillRequest request) {
        try {
            KakaoSkillRequest.UserRequest.Media media = request.getUserRequest().getParams().getMedia();
            if (media != null && "image".equalsIgnoreCase(media.getType()) && StringUtils.hasText(media.getUrl())) {
                return Optional.of(media.getUrl().trim());
            }
        } catch (NullPointerException ignored) {
            // params/media 없음 → 이미지 아님
        }
        return Optional.empty();
    }

    private KakaoSkillResponse handleLinkCode(String botUserKey, String code, Optional<User> userOpt) {
        if (userOpt.isPresent()) {
            return KakaoSkillResponse.text("이미 연동된 계정이에요 ✅");
        }

        Optional<Long> linkedUserId = linkCodeService.resolveCode(code);
        if (linkedUserId.isEmpty()) {
            return KakaoSkillResponse.text(
                    "코드가 올바르지 않거나 만료됐어요.\n웹앱에서 새 코드를 받아주세요."
            );
        }

        userRepository.findById(linkedUserId.get()).ifPresent(user -> {
            user.updateBotUserKey(botUserKey);
            userRepository.save(user);
            log.info("Bot linked: userId={} botUserKey={}", user.getId(), botUserKey);
        });

        return KakaoSkillResponse.text(
                "연동 완료! ✅\n이제 링크나 텍스트를 보내면 자동으로 저장돼요."
        );
    }

    private String extractBotUserKey(KakaoSkillRequest request) {
        try {
            return request.getUserRequest().getUser().getId();
        } catch (NullPointerException e) {
            return null;
        }
    }

    private String extractUtterance(KakaoSkillRequest request) {
        try {
            return request.getUserRequest().getUtterance();
        } catch (NullPointerException e) {
            return null;
        }
    }
}
