package com.curio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoUserResponse {

    private Long id;
    private KakaoProperties properties;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KakaoProperties {
        private String nickname;

        @JsonProperty("profile_image")
        private String profileImage;
    }

    public String getNickname() {
        return properties != null ? properties.getNickname() : "";
    }

    public String getProfileImageUrl() {
        return properties != null ? properties.getProfileImage() : null;
    }
}
