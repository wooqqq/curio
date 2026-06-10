package com.curio.security;

import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 권한 판별. role 컬럼을 두지 않고 ADMIN_KAKAO_IDS 환경변수(쉼표 구분)에
 * 등록된 kakao_id를 가진 유저만 관리자로 본다. 1인 운영 전제의 단순 allowlist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final UserRepository userRepository;

    @Value("${admin.kakao-ids:}")
    private String adminKakaoIdsRaw;

    private Set<String> adminKakaoIds;

    @PostConstruct
    void init() {
        adminKakaoIds = Arrays.stream(adminKakaoIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (adminKakaoIds.isEmpty()) {
            log.warn("ADMIN_KAKAO_IDS 미설정 — 관리자 기능을 사용할 수 없습니다.");
        }
    }

    public boolean isAdmin(Long userId) {
        if (userId == null || adminKakaoIds.isEmpty()) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> adminKakaoIds.contains(user.getKakaoId()))
                .orElse(false);
    }

    /** 관리자가 아니면 FORBIDDEN. 관리자 전용 엔드포인트 진입부에서 호출. */
    public void verify(Long userId) {
        if (!isAdmin(userId)) {
            throw new CurioException(ErrorCode.FORBIDDEN);
        }
    }
}
