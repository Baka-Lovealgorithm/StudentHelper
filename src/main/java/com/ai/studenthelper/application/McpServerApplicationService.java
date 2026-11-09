package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.McpToolkitFactory;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.McpServerRequest;
import com.ai.studenthelper.common.dto.McpServerResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.McpServerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * T-301 远程 MCP Server 管理（DD-10）：只接受 http / https 的远程传输，
 * 明确拒绝 stdio（不启动本机子进程）与其它协议。连接测试失败只记状态，不抛业务异常打断流程。
 */
@Service
public class McpServerApplicationService {

    private static final Set<String> TRANSPORTS = Set.of("STREAMABLE_HTTP", "SSE");
    private static final int MAX_NAME = 32;
    private static final int MAX_URL = 512;

    private final McpServerRepository repository;
    private final McpToolkitFactory toolkitFactory;
    private final S2FeatureService features;

    public McpServerApplicationService(McpServerRepository repository,
                                       McpToolkitFactory toolkitFactory,
                                       S2FeatureService features) {
        this.repository = repository;
        this.toolkitFactory = toolkitFactory;
        this.features = features;
    }

    public record TestResult(Long id, String status, int toolCount, List<String> tools, String error) {
    }

    public List<McpServerResponse> list() {
        features.requireEnabled(S2Feature.MCP);
        return repository.findAll();
    }

    public McpServerResponse create(McpServerRequest request) {
        features.requireEnabled(S2Feature.MCP);
        String name = normalizeName(request);
        String transport = normalizeTransport(request);
        String url = normalizeUrl(request);
        if (repository.nameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 已存在：" + name);
        }
        boolean enabled = request.enabled() != null && request.enabled();
        Long id = repository.insert(name, transport, url, enabled);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "MCP Server 创建失败"));
    }

    public McpServerResponse update(Long id, McpServerRequest request) {
        features.requireEnabled(S2Feature.MCP);
        requireServer(id);
        String name = normalizeName(request);
        String transport = normalizeTransport(request);
        String url = normalizeUrl(request);
        if (repository.nameExists(name, id)) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 已存在：" + name);
        }
        repository.update(id, name, transport, url, request.enabled());
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "MCP Server 不存在"));
    }

    public TestResult test(Long id) {
        features.requireEnabled(S2Feature.MCP);
        McpServerResponse server = requireServer(id);
        try {
            List<String> tools = toolkitFactory.discover(server);
            repository.updateStatus(id, "OK", null, tools.size());
            return new TestResult(id, "OK", tools.size(), tools, null);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String trimmed = reason.length() > 300 ? reason.substring(0, 300) : reason;
            repository.updateStatus(id, "FAILED", trimmed, 0);
            return new TestResult(id, "FAILED", 0, new ArrayList<>(), trimmed);
        }
    }

    public void delete(Long id) {
        features.requireEnabled(S2Feature.MCP);
        requireServer(id);
        repository.delete(id);
    }

    private McpServerResponse requireServer(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server id 必填");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "MCP Server 不存在"));
    }

    private static String normalizeName(McpServerRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 名称不能为空");
        }
        String name = request.name().trim();
        if (name.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 名称不超过 " + MAX_NAME + " 字");
        }
        return name;
    }

    private static String normalizeTransport(McpServerRequest request) {
        String transport = request == null || request.transport() == null || request.transport().isBlank()
                ? "STREAMABLE_HTTP" : request.transport().trim().toUpperCase(Locale.ROOT);
        if (!TRANSPORTS.contains(transport)) {
            throw new BusinessException(ErrorCode.E1004, "传输方式只支持 STREAMABLE_HTTP / SSE（不支持 stdio）");
        }
        return transport;
    }

    private static String normalizeUrl(McpServerRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 地址不能为空");
        }
        String url = request.url().trim();
        if (url.length() > MAX_URL) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 地址不超过 " + MAX_URL + " 字符");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new BusinessException(ErrorCode.E1004, "MCP Server 地址必须以 http:// 或 https:// 开头");
        }
        return url;
    }
}
