package com.curio.entity;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemType type;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 1000)
    private String thumbnailUrl;

    @Column(length = 2000)
    private String originalUrl;

    @Column(length = 2000)
    private String normalizedUrl;

    @Column(length = 500)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status = ItemStatus.UNREAD;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

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
        this.title = title;
        this.content = content;
        this.thumbnailUrl = thumbnailUrl;
        this.originalUrl = originalUrl;
        this.normalizedUrl = normalizedUrl;
        this.s3Key = s3Key;
        this.category = category;
        this.status = ItemStatus.UNREAD;
    }

    public void addTag(Tag tag) {
        this.tags.add(tag);
    }

    public void updateCategory(Category category) {
        this.category = category;
    }

    public void updateStatus(ItemStatus status) {
        this.status = status;
    }
}
