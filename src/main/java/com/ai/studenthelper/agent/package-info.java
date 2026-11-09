/**
 * Agent 层：AgentScope 适配、系统 Prompt、上下文组装、输出分段。
 * 允许依赖 infra（只读配置）与 domain；禁止访问数据库、禁止感知 HTTP / SSE。
 * S1 任务 T-101 / T-102 / T-108 / T-109 在此实现；S0 仅建立包位置。
 */
package com.ai.studenthelper.agent;
