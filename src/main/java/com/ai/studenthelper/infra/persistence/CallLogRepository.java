package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.domain.CallLog;
import org.springframework.data.repository.CrudRepository;

/** 调用日志持久化：只写不对外查询（AC-111-3）。 */
public interface CallLogRepository extends CrudRepository<CallLog, Long> {
}
