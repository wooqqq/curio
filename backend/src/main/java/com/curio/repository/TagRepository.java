package com.curio.repository;

import com.curio.entity.Tag;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    /**
     * 태그를 원자적으로 insert한다. 동시에 같은 이름이 들어와 unique(name) 충돌이 나도 예외를
     * 던지지 않고(INSERT IGNORE) 조용히 건너뛴다. saveAndFlush가 충돌 시 던지던
     * DataIntegrityViolationException은 본 트랜잭션을 rollback-only로 오염시켜, "복구한 듯"
     * 진행해도 커밋 때 UnexpectedRollbackException(500) + 저장 유실로 이어졌다 — 그 예외 경로 자체를 없앤다.
     * MySQL 전용. 삽입 여부와 무관하게 직후 findByNameForUpdate로 행을 확보한다.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO tags (name) VALUES (:name)", nativeQuery = true)
    void insertIgnore(@Param("name") String name);

    /**
     * 잠금 읽기(SELECT ... FOR UPDATE)로 '최신 커밋' 행을 읽는다. MySQL 기본 격리수준
     * REPEATABLE READ에서 일반 SELECT는 트랜잭션 첫 읽기 시점의 스냅샷을 보므로, 그 뒤
     * 다른 트랜잭션이 커밋한 태그(동시 생성분)를 못 볼 수 있다. insertIgnore 직후의 재조회는
     * 이 잠금 읽기로 해야 스냅샷을 우회해 동시 생성된 태그까지 확실히 잡는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tag t where t.name = :name")
    Optional<Tag> findByNameForUpdate(@Param("name") String name);
}
