package com.curio.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoSkillRequest {

    private UserRequest userRequest;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserRequest {
        private String utterance;
        private User user;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class User {
            private String id;
            private String type;
        }
    }
}
