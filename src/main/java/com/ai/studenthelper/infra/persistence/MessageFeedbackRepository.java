package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.domain.MessageFeedback;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 反馈标记持久化（T-202 错题本 / T-207 反馈闭环共用）。 */
public interface MessageFeedbackRepository extends CrudRepository<MessageFeedback, Long> {

    @Query("SELECT * FROM t_message_feedback WHERE message_id = :messageId AND kind = :kind")
    Optional<MessageFeedback> findByMessageIdAndKind(@Param("messageId") Long messageId,
                                                     @Param("kind") String kind);

    @Query("SELECT * FROM t_message_feedback WHERE session_id = :sessionId ORDER BY created_at DESC, id DESC")
    List<MessageFeedback> findBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT * FROM t_message_feedback WHERE kind = :kind ORDER BY created_at DESC, id DESC")
    List<MessageFeedback> findByKind(@Param("kind") String kind);

    @Query("SELECT * FROM t_message_feedback ORDER BY created_at DESC, id DESC")
    List<MessageFeedback> findAllOrdered();

    @Modifying
    @Query("DELETE FROM t_message_feedback WHERE session_id = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("DELETE FROM t_message_feedback WHERE message_id = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);
}
