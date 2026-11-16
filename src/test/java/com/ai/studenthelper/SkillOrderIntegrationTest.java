package com.ai.studenthelper;

import com.ai.studenthelper.common.dto.SkillResponse;
import com.ai.studenthelper.infra.persistence.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-052 / AC-517-1：技能列表固定顺序。
 *
 * 走真实 MySQL，但整个测试方法在事务内执行并回滚：翻转 enabled 只用于验证
 * 「启停不改变展示顺序」，测试结束不留任何数据改动（也不触碰 updated_at）。
 * 需要 DB_PASSWORD 环境变量；未设置时整体跳过，保证日常 mvn test 不依赖数据库。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@org.springframework.test.context.TestPropertySource(
        properties = "studenthelper.memory.dir=./target/test-memory")
class SkillOrderIntegrationTest {

    @Autowired
    private SkillRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findAllUsesFixedIdOrderRegardlessOfEnabled() {
        List<SkillResponse> before = repository.findAll();
        assertTrue(before.size() >= 2, "需要至少 2 条技能才能验证相对顺序");

        List<Long> ids = new ArrayList<>(before.stream().map(SkillResponse::id).toList());
        List<Long> ascending = new ArrayList<>(ids);
        ascending.sort(Long::compareTo);
        assertEquals(ascending, ids, "列表必须按 id 升序（创建顺序）返回，不再按 enabled 优先");

        // 事务内翻转第一条技能的启用态，验证展示顺序不受影响；回滚后数据库恢复原状。
        long firstId = ids.get(0);
        Integer current = jdbc.queryForObject(
                "SELECT enabled FROM t_skill WHERE id = ?", Integer.class, firstId);
        assertTrue(current != null, "技能行应存在");
        jdbc.update("UPDATE t_skill SET enabled = ? WHERE id = ?", current == 1 ? 0 : 1, firstId);

        List<Long> after = repository.findAll().stream().map(SkillResponse::id).toList();
        assertEquals(ids, after, "启停技能后列表顺序必须保持不变");
    }
}
