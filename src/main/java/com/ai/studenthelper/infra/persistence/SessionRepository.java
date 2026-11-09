package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.domain.Session;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 会话持久化：基本 CRUD + 按更新时间倒序列表 / 分页（FR-104）。 */
public interface SessionRepository extends CrudRepository<Session, Long> {

    @Query("SELECT * FROM t_session ORDER BY updated_at DESC, id DESC")
    List<Session> findAllOrdered();

    /** 分页按「更新时间倒序 + id 倒序」，与 findAllOrdered 顺序一致。 */
    @Query("SELECT * FROM t_session ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset")
    List<Session> findPage(@Param("limit") int limit, @Param("offset") long offset);
}
