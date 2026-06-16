package com.curio.entity;

import com.curio.common.TextUtils;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

    public static final int NAME_MAX = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = NAME_MAX)
    private String name;

    @Builder
    public Tag(String name) {
        this.name = TextUtils.truncate(name, NAME_MAX);
    }
}
