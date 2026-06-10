package com.curio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 아카이브 진입 시 노출되는 팝업 배너. 활성(active)은 항상 최대 1개만 유지된다(서비스에서 보장).
 * 이미지 클릭 시 linkUrl로 이동하며, linkUrl에 공지 상세 경로(/announcements/{id})를 넣어
 * 공지와 느슨하게 연결한다(외부 URL도 허용).
 */
@Entity
@Table(name = "popups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    /** 사진 대신 텍스트로 노출할 때 사용 (선택). */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** S3에 업로드된 팝업 이미지 공개 URL (선택). */
    @Column(length = 1000)
    private String imageUrl;

    /** 팝업 클릭 시 이동할 주소. 보통 /announcements/{id}, 외부 URL도 가능. */
    @Column(length = 2000)
    private String linkUrl;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Popup(String title, String content, String imageUrl, String linkUrl, boolean active) {
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.active = active;
    }

    public void update(String title, String content, String imageUrl, String linkUrl, boolean active) {
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }
}