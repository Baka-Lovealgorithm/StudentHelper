package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.domain.ModelConfig;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

/** 模型配置持久化：读当前生效配置、事务内切换 is_current（AC-105-2）。 */
public interface ModelConfigRepository extends CrudRepository<ModelConfig, Long> {

    @Query("SELECT * FROM t_model_config ORDER BY created_at DESC, id DESC")
    List<ModelConfig> findAllOrdered();

    @Query("SELECT * FROM t_model_config WHERE is_current = 1 ORDER BY id DESC LIMIT 1")
    Optional<ModelConfig> findCurrent();

    @Modifying
    @Query("UPDATE t_model_config SET is_current = 0 WHERE is_current = 1")
    void clearCurrent();
}
