package com.curio.service;

import com.curio.entity.RefreshToken;
import com.curio.entity.User;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.repository.RefreshTokenRepository;
import com.curio.repository.UserRepository;
import com.curio.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public Map<String, String> reissue(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new CurioException(ErrorCode.INVALID_TOKEN);
        }

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CurioException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new CurioException(ErrorCode.EXPIRED_TOKEN);
        }

        // 회전 없이 access 토큰만 새로 발급한다. refresh 토큰은 만료/로그아웃까지 그대로 유지.
        // (매 페이지 로드마다 회전하면 새로고침 연타·멀티탭에서 race로 로그아웃되던 문제 제거)
        String newAccessToken = jwtUtil.generateAccessToken(stored.getUser().getId());
        return Map.of("accessToken", newAccessToken, "refreshToken", refreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CurioException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.deleteByUser(user);
    }
}
