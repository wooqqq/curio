package com.curio.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoSkillRequest {

    private UserRequest userRequest;
    // 사진 전송 시 오픈빌더가 채우는 트리거. type="IMAGE_UPLOAD"면 이미지 업로드다(실측, 설계결정 #30).
    private Flow flow;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserRequest {
        private String utterance;
        private User user;
        // 이미지 업로드 시 media.url에 이미지 URL이 들어온다(utterance에도 같은 URL이 실리지만 명시 필드를 우선 사용).
        private Params params;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class User {
            private String id;
            private String type;
        }

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Params {
            private Media media;
        }

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Media {
            private String type; // "image"
            private String url;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Flow {
        private Trigger trigger;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Trigger {
            private String type; // 예: "IMAGE_UPLOAD"
        }
    }
}
