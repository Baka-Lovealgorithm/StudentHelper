package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.domain.Session;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/** 会话持久化：基本 CRUD + 按更新时间倒序列表（FR-104）。 */
public interface SessionRepository extends CrudRepository<Session, Long> {

    @Query("SELECT * FROM t_session ORDER BY updated_at DESC, id DESC")
    List<Session> findAllOrdered();
}
