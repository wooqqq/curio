package com.curio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LinkCodeService {

    private static final String PREFIX = "link:";
    private static final long TTL_MINUTES = 10;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode(Long userId) {
        String code = randomCode();
        redisTemplate.opsForValue().set(PREFIX + code, userId.toString(), TTL_MINUTES, TimeUnit.MINUTES);
        return code;
    }

    public Optional<Long> resolveCode(String code) {
        String val = redisTemplate.opsForValue().get(PREFIX + code.toUpperCase());
        if (val == null) return Optional.empty();
        redisTemplate.delete(PREFIX + code.toUpperCase());
        return Optional.of(Long.parseLong(val));
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
