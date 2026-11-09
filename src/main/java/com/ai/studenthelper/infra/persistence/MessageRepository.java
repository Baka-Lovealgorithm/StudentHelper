package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.domain.Message;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 消息持久化：按会话查消息、删除会话时级联清理（FR-104）。 */
public interface MessageRepository extends CrudRepository<Message, Long> {

    @Query("SELECT * FROM t_message WHERE session_id = :sessionId ORDER BY created_at ASC, id ASC")
    List<Message> findBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("DELETE FROM t_message WHERE session_id = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
