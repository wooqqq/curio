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

import java.time.LocalDateTime;
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

        User user = stored.getUser();

        // refresh token rotation
        refreshTokenRepository.delete(stored);
        String newAccessToken = jwtUtil.generateAccessToken(user.getId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(newRefreshToken)
                .expiredAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshExpiration() / 1000))
                .build());

        return Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CurioException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.deleteByUser(user);
    }
}
