package com.curio.dto.kakao;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KakaoSkillResponse {

    @Builder.Default
    private String version = "2.0";
    private Template template;

    @Getter
    @Builder
    public static class Template {
        private List<Output> outputs;
    }

    @Getter
    @Builder
    public static class Output {
        private SimpleText simpleText;
    }

    @Getter
    @Builder
    public static class SimpleText {
        private String text;
    }

    public static KakaoSkillResponse text(String message) {
        return KakaoSkillResponse.builder()
                .template(Template.builder()
                        .outputs(List.of(Output.builder()
                                .simpleText(SimpleText.builder()
                                        .text(message)
                                        .build())
                                .build()))
                        .build())
                .build();
    }
}