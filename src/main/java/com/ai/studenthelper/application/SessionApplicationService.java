package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.MessageResponse;
import com.ai.studenthelper.common.dto.SessionDetailResponse;
import com.ai.studenthelper.common.dto.SessionPageResponse;
import com.ai.studenthelper.common.dto.SessionResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.ai.studenthelper.infra.persistence.ProcessRepository;
import com.ai.studenthelper.infra.persistence.StepUnlockRepository;
import com.ai.studenthelper.infra.persistence.SessionRepository;
import com.ai.studenthelper.infra.persistence.SkillRepository;
import com.ai.studenthelper.infra.memory.SessionSummaryStore;
import com.ai.studenthelper.infra.storage.ImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 会话用例编排：会话与历史的读写、级联清理、事务边界（FR-104）。 */
@Service
public class SessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SessionApplicationService.class);

    /** CR-038：会话固化技能上限，与按需读入预算一致（最多 2 条）。 */
    public static final int MAX_SESSION_SKILLS = SkillApplicationService.MAX_INJECTED_SKILLS;

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final ImageStorage imageStorage;
    private final KnowledgePointRepository knowledgePointRepository;
    private final StepUnlockRepository stepUnlockRepository;
    private final ModelProviderRepository modelProviderRepository;
    private final SessionSummaryStore sessionSummaryStore;
    /** CR-027：可选依赖（单测直连构造时为 null），删除会话时清理过程行。 */
    @Autowired(required = false)
    private ProcessRepository processRepository;
    /** CR-038：可选依赖（单测直连构造时为 null），用于校验固化技能 id 是否存在且启用。 */
    @Autowired(required = false)
    private SkillRepository skillRepository;

    public SessionApplicationService(SessionRepository sessionRepository,
                                     MessageRepository messageRepository,
                                     MessageFeedbackRepository feedbackRepository,
                                     ImageStorage imageStorage,
                                     KnowledgePointRepository knowledgePointRepository,
                                     StepUnlockRepository stepUnlockRepository,
                                     ModelProviderRepository modelProviderRepository,
                                     SessionSummaryStore sessionSummaryStore) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.feedbackRepository = feedbackRepository;
        this.imageStorage = imageStorage;
        this.knowledgePointRepository = knowledgePointRepository;
        this.stepUnlockRepository = stepUnlockRepository;
        this.modelProviderRepository = modelProviderRepository;
        this.sessionSummaryStore = sessionSummaryStore;
    }

    public SessionResponse create(String title) {
        LocalDateTime now = LocalDateTime.now();
        Session session = new Session();
        session.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim());
        session.setGuideMode(0);
        // CR-012：新会话继承全局默认模型（迁移后为原 is_current 对应条目）
        session.setModelId(modelProviderRepository.findDefaultModelId());
        // CR-018：新会话默认「模型自选」；首轮判定后再固化到 subject_skill_id
        session.setSubjectMode(SubjectSkillRuntime.Mode.AUTO.name());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return toResponse(sessionRepository.save(session));
    }

    public List<SessionResponse> list() {
        return sessionRepository.findAllOrdered().stream().map(this::toResponse).toList();
    }

    /**
     * 会话分页：按 updated_at / id 倒序，offset 由 page * size 计算。
     * 前端只有「加载更多」，不暴露页码跳转；page 从 0 开始。
     */
    public SessionPageResponse listPage(int page, int size) {
        long total = sessionRepository.count();
        long offset = (long) page * size;
        List<SessionResponse> items = sessionRepository.findPage(size, offset).stream()
                .map(this::toResponse).toList();
        return new SessionPageResponse(items, total, offset + items.size() < total);
    }

    /**
     * 重命名会话。刻意不更新 updated_at：重命名不是对话活动，避免会话在列表里
     * 跳到首位、打断用户正在浏览的位置。
     */
    public SessionResponse rename(Long id, String title) {
        Session session = requireSession(id);
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.E1004);
        }
        session.setTitle(normalized);
        return toResponse(sessionRepository.save(session));
    }

    /** CR-033：模型自动标题的默认值口径；仅当标题仍为该值时才允许模型写回。 */
    public static final String DEFAULT_TITLE = "新会话";

    /**
     * CR-033：写回模型生成的首轮标题（T-104 / AC-104-12~13）。
     * 仅当会话标题仍为「新会话」时写入；用户手动重命名过的标题永不覆盖；
     * 生成失败 / 解析不到时调用方不调用本方法。与 rename() 同口径：不更新 updated_at，
     * 避免首轮回答把会话顶到列表首位之外的位置变化。
     */
    public void updateAutoTitle(Long id, String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        Session session = requireSession(id);
        if (!DEFAULT_TITLE.equals(session.getTitle())) {
            return;
        }
        session.setTitle(title.trim());
        sessionRepository.save(session);
    }

    /** CR-012：会话级模型选择，只改当前会话、不动 updated_at（不打断列表排序）。 */
    public SessionResponse setModel(Long id, Long modelId) {
        Session session = requireSession(id);
        if (modelId == null || modelProviderRepository.findModel(modelId).isEmpty()) {
            throw new BusinessException(ErrorCode.E3003, "模型不存在或已被删除");
        }
        session.setModelId(modelId);
        return toResponse(sessionRepository.save(session));
    }

    /**
     * CR-018：会话级学科三态。只改当前会话、不动 updated_at（沿用 CR-012 语义）。
     * 切换到非 MANUAL 时清空已固化条目，下一次完整解题重新判定。
     */
    public SessionResponse setSubject(Long id, String mode, Long subjectSkillId) {
        Session session = requireSession(id);
        SubjectSkillRuntime.Mode parsed = SubjectSkillApplicationService.parseMode(mode);
        if (parsed == SubjectSkillRuntime.Mode.MANUAL && subjectSkillId == null) {
            throw new BusinessException(ErrorCode.E1004, "手动指定学科时 subjectSkillId 必填");
        }
        session.setSubjectMode(parsed.name());
        session.setSubjectSkillId(parsed == SubjectSkillRuntime.Mode.MANUAL ? subjectSkillId : null);
        return toResponse(sessionRepository.save(session));
    }

    /** 供 subject_get 工具固化命中结果；与前端切换共用同一列。 */
    public SessionResponse bindSubject(Long id, Long subjectSkillId) {
        Session session = requireSession(id);
        session.setSubjectSkillId(subjectSkillId);
        return toResponse(sessionRepository.save(session));
    }

    /** CR-038：把 "1,9" 解析成去重且保序的 id 列表；非法段跳过，不阻断会话读取。 */
    public static List<Long> parseSkillIds(String raw) {
        List<Long> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0 && !out.contains(id)) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 历史脏数据按空处理
            }
        }
        return out;
    }

    private static String joinSkillIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null
                : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** CR-038：手动设置 / 清除当前会话固化的通用技能；空列表清除回「自动」，不更新 updated_at。 */
    public SessionResponse setSkills(Long id, List<Long> skillIds) {
        Session session = requireSession(id);
        List<Long> ids = skillIds == null ? List.of()
                : skillIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_SESSION_SKILLS) {
            throw new BusinessException(ErrorCode.E1004,
                    "一个会话最多固化 " + MAX_SESSION_SKILLS + " 条通用技能");
        }
        if (skillRepository != null) {
            for (Long skillId : ids) {
                var found = skillRepository.findById(skillId);
                if (found.isEmpty() || !found.get().enabled()) {
                    throw new BusinessException(ErrorCode.E1004, "技能不存在或未启用：" + skillId);
                }
            }
        }
        session.setSkillIds(joinSkillIds(ids));
        log.info("SESSION_SKILL_SET sessionId={} skillIds={}", id, ids);
        return toResponse(sessionRepository.save(session));
    }

    /** CR-038：供 skill_get 命中时追加固化；去重、上限 2、失败不阻断工具返回。 */
    public void bindSkill(Long id, Long skillId) {
        if (id == null || skillId == null) {
            return;
        }
        try {
            Session session = requireSession(id);
            List<Long> ids = new ArrayList<>(parseSkillIds(session.getSkillIds()));
            if (ids.contains(skillId) || ids.size() >= MAX_SESSION_SKILLS) {
                return;
            }
            ids.add(skillId);
            session.setSkillIds(joinSkillIds(ids));
            sessionRepository.save(session);
            log.info("SESSION_SKILL_BIND sessionId={} skillId={} total={}", id, skillId, ids.size());
        } catch (Exception e) {
            log.warn("SESSION_SKILL_BIND_FAILED sessionId={} skillId={} reason={}",
                    id, skillId, e.getMessage());
        }
    }

    public SessionDetailResponse detail(Long id) {
        Session session = requireSession(id);
        List<MessageResponse> messages = messageRepository.findBySessionId(id).stream()
                .map(this::toResponse).toList();
        return new SessionDetailResponse(toResponse(session), messages);
    }

    @Transactional
    public void delete(Long id) {
        requireSession(id);
        // S2：反馈标记随会话级联清理，避免孤儿行（AC-202-3 / AC-207-4）
        feedbackRepository.deleteBySessionId(id);
        knowledgePointRepository.removeSessionHits(id);
        // CR-066：t_practice 停止写入、表与存量留档，不再级联清理；t_step_unlock 仍随会话清理
        stepUnlockRepository.deleteBySessionId(id);
        // CR-027：过程表按 message_id 关联，必须在删消息之前清理
        if (processRepository != null) {
            processRepository.deleteBySessionId(id);
        }
        messageRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);
        imageStorage.deleteSessionDir(id);
        // TCR-040 / AC-401-10：压缩摘要按会话缓存，随会话一并清理，避免孤儿文件累积
        sessionSummaryStore.delete(id);
    }

    @Transactional
    public void deleteMessage(Long id) {
        feedbackRepository.deleteByMessageId(id);
        knowledgePointRepository.removeMessageHits(id);
        stepUnlockRepository.deleteByMessageId(id);
        messageRepository.deleteById(id);
    }

    public SessionResponse updateGuideMode(Long id, int guideMode) {
        Session session = requireSession(id);
        session.setGuideMode(guideMode);
        session.setUpdatedAt(LocalDateTime.now());
        return toResponse(sessionRepository.save(session));
    }

    /** 供解题主链路（T-101 起）复用。 */
    public Session requireSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1003));
    }

    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    private SessionResponse toResponse(Session s) {
        return new SessionResponse(s.getId(), s.getTitle(), s.getGuideMode(), s.getModelId(),
                s.getCreatedAt(), s.getUpdatedAt(),
                s.getSubjectMode() == null ? SubjectSkillRuntime.Mode.AUTO.name() : s.getSubjectMode(),
                s.getSubjectSkillId(), parseSkillIds(s.getSkillIds()));
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getHint(), m.getImagePaths(),
                m.getStatus(), m.getErrorCode(), m.getCreatedAt(),
                m.getSectionSchema(), m.getSubjectLabel(), m.getIntent());
    }
}
