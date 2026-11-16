package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.KnowledgePageResponse;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * T-203 知识点用例；关闭态拒绝读写（DD-01）。
 *
 * <p>CR-040：新增手动新增与删除，列表支持按创建日期起止区间筛选；掌握度退役。</p>
 */
@Service
public class KnowledgeApplicationService {

    /** 列宽：subject 64、parent_name / name 128（与表结构一致，超长直接拒绝而不是静默截断）。 */
    private static final int MAX_SUBJECT_CHARS = 64;
    private static final int MAX_NAME_CHARS = 128;
    /** 单页上限：与 `/api/sessions/page` 的 50 保持一致口径，避免一次拉爆列表。 */
    private static final int MAX_PAGE_SIZE = 50;

    private final KnowledgePointRepository repository;
    private final S2FeatureService features;

    public KnowledgeApplicationService(KnowledgePointRepository repository, S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    /**
     * CR-040：列表。{@code from} / {@code to} 为 {@code yyyy-MM-dd} 起止区间（闭区间、可只填一端），
     * 与学科筛选叠加生效；任一格式非法或起始晚于结束返回 {@code E1004}。
     */
    public List<KnowledgePointResponse> list(String subject, String from, String to) {
        features.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT);
        LocalDate[] range = resolveRange(from, to);
        return repository.list(subject, range[0], range[1]);
    }

    /**
     * CR-040：分页列表（前端「更多」专用）。筛选与排序口径与 {@link #list} 完全一致；
     * {@code page} 从 0 开始、{@code size} 夹在 1~{@value #MAX_PAGE_SIZE}（与既有分页接口一致，越界不抛错而是收敛）。
     */
    public KnowledgePageResponse page(String subject, String from, String to, int page, int size) {
        features.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT);
        LocalDate[] range = resolveRange(from, to);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        long offset = (long) safePage * safeSize;
        List<KnowledgePointResponse> items = repository.listPage(subject, range[0], range[1], safeSize, offset);
        long total = repository.count(subject, range[0], range[1]);
        return new KnowledgePageResponse(items, total, offset + items.size() < total);
    }

    /** CR-025：知识点学科去重清单，供前端下拉使用。 */
    public List<String> subjects() {
        features.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT);
        return repository.distinctSubjects();
    }

    /**
     * CR-040：手动新增。命中次数固定 0；与自动抽取共用唯一键，重复时明确报错而不覆盖原记录。
     */
    @Transactional
    public KnowledgePointResponse create(String subject, String parentName, String name) {
        features.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT);
        String normalizedSubject = requireText(subject, "学科", MAX_SUBJECT_CHARS);
        String normalizedName = requireText(name, "知识点名称", MAX_NAME_CHARS);
        String normalizedParent = parentName == null ? "" : parentName.trim();
        if (normalizedParent.length() > MAX_NAME_CHARS) {
            throw new BusinessException(ErrorCode.E1004, "大知识点不能超过 " + MAX_NAME_CHARS + " 字");
        }
        if (repository.exists(normalizedSubject, normalizedParent, normalizedName)) {
            throw new BusinessException(ErrorCode.E1004, "知识点已存在：" + normalizedSubject
                    + " / " + (normalizedParent.isEmpty() ? normalizedName : normalizedParent + " > " + normalizedName));
        }
        Long id = repository.insertManual(normalizedSubject, normalizedParent, normalizedName);
        if (id == null) {
            throw new BusinessException(ErrorCode.E5000, "知识点新增失败");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E5000, "知识点新增失败"));
    }

    /** CR-040：删除条目并级联清理命中明细；不存在返回 {@code E1005}。 */
    @Transactional
    public void delete(Long id) {
        features.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT);
        if (id == null) {
            throw new BusinessException(ErrorCode.E1004, "知识点 id 必填");
        }
        if (repository.delete(id) == 0) {
            throw new BusinessException(ErrorCode.E1005, "知识点不存在：" + id);
        }
    }

    /** 日期区间校验：格式非法或起始晚于结束都抛 {@code E1004}；列表与分页共用。 */
    private static LocalDate[] resolveRange(String from, String to) {
        LocalDate fromDate = parseDate(from, "起始日期");
        LocalDate toDate = parseDate(to, "结束日期");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.E1004, "起始日期不能晚于结束日期");
        }
        return new LocalDate[]{fromDate, toDate};
    }

    private static LocalDate parseDate(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.E1004, label + "格式应为 yyyy-MM-dd");
        }
    }

    private static String requireText(String raw, String label, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.E1004, label + "必填");
        }
        if (value.length() > max) {
            throw new BusinessException(ErrorCode.E1004, label + "不能超过 " + max + " 字");
        }
        return value;
    }
}
