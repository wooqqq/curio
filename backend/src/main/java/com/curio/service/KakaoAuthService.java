package com.curio.service;

import com.curio.dto.KakaoTokenResponse;
import com.curio.dto.KakaoUserResponse;
import com.curio.entity.RefreshToken;
import com.curio.entity.User;
import com.curio.repository.RefreshTokenRepository;
import com.curio.repository.UserRepository;
import com.curio.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.token-uri}")
    private String tokenUri;

    @Value("${kakao.user-info-uri}")
    private String userInfoUri;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Transactional
    public Map<String, String> login(String code) {
        String kakaoAccessToken = getKakaoAccessToken(code);
        KakaoUserResponse userInfo = getKakaoUserInfo(kakaoAccessToken);

        User user = userRepository.findByKakaoId(String.valueOf(userInfo.getId()))
                .map(existing -> {
                    existing.update(userInfo.getNickname(), userInfo.getProfileImageUrl());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId(String.valueOf(userInfo.getId()))
                        .nickname(userInfo.getNickname())
                        .profileImageUrl(userInfo.getProfileImageUrl())
                        .build()));

        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiredAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshExpiration() / 1000))
                .build());

        return Map.of("accessToken", accessToken, "refreshToken", refreshToken);
    }

    private String getKakaoAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<KakaoTokenResponse> response = restTemplate.postForEntity(
                tokenUri, request, KakaoTokenResponse.class);

        return response.getBody().getAccessToken();
    }

    private KakaoUserResponse getKakaoUserInfo(String kakaoAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kakaoAccessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<KakaoUserResponse> response = restTemplate.exchange(
                userInfoUri, HttpMethod.GET, request, KakaoUserResponse.class);

        return response.getBody();
    }

    public String getFrontendUrl() {
        return allowedOrigins.split(",")[0];
    }
}
