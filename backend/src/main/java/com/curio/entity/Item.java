package com.curio.entity;

import com.curio.common.TextUtils;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemStatus;
import com.curio.entity.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "items",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_normalized_url",
        columnNames = {"user_id", "normalized_url"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    public static final int TITLE_MAX = 500;
    public static final int THUMBNAIL_URL_MAX = 1000;
    public static final int URL_MAX = 2000;
    public static final int S3_KEY_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemType type;

    @Column(length = TITLE_MAX)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = THUMBNAIL_URL_MAX)
    private String thumbnailUrl;

    @Column(length = URL_MAX)
    private String originalUrl;

    @Column(length = URL_MAX)
    private String normalizedUrl;

    @Column(length = S3_KEY_MAX)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status = ItemStatus.UNREAD;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "item_tags",
        joinColumns = @JoinColumn(name = "item_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Item(User user, ItemType type, String title, String content,
                String thumbnailUrl, String originalUrl, String normalizedUrl,
                String s3Key, Category category) {
        this.user = user;
        this.type = type;
        // 크롤 제목·URL이 컬럼 길이를 넘으면 strict 모드 insert가 롤백되므로 저장 전 자른다.
        // content는 TEXT 컬럼이라 제한 없음.
        this.title = TextUtils.truncate(title, TITLE_MAX);
        this.content = content;
        this.thumbnailUrl = TextUtils.truncate(thumbnailUrl, THUMBNAIL_URL_MAX);
        this.originalUrl = TextUtils.truncate(originalUrl, URL_MAX);
        this.normalizedUrl = TextUtils.truncate(normalizedUrl, URL_MAX);
        this.s3Key = TextUtils.truncate(s3Key, S3_KEY_MAX);
        this.category = category;
        this.status = ItemStatus.UNREAD;
    }

    public void addTag(Tag tag) {
        // 같은 태그를 중복으로 담지 않는다(item_tags 중복 조인 행/중복 표시 방지). AI 태그 dedup(#cdf9e7c)은
        // 원본 문자열로만 거르므로, 긴 태그가 50자로 잘려(#26·#28) 같은 Tag로 수렴하는 경계까지 여기서 닫는다.
        // 같은 행은 같은 영속성 컨텍스트에서 동일 인스턴스라 identity contains로 충분하다.
        if (!this.tags.contains(tag)) {
            this.tags.add(tag);
        }
    }

    /** 재분류 시 기존 태그를 비운다(join 행만 제거, 공유 Tag는 유지). 중복 누적 방지. */
    public void clearTags() {
        this.tags.clear();
    }

    public void updateCategory(Category category) {
        this.category = category;
    }

    public void updateLinkMetadata(String title, String content, String thumbnailUrl) {
        this.title = TextUtils.truncate(title, TITLE_MAX);
        this.content = content;
        this.thumbnailUrl = TextUtils.truncate(thumbnailUrl, THUMBNAIL_URL_MAX);
    }

    public void updateStatus(ItemStatus status) {
        this.status = status;
    }
}
