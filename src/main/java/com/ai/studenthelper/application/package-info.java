/**
 * Application 层：用例编排与事务边界（会话、解题流程、模型配置）。
 * 允许依赖 agent / infra / domain，禁止包含 HTTP 与 SSE 协议细节。
 * S1 任务 T-101~T-113 在此实现；S0 仅建立包位置。
 */
package com.ai.studenthelper.application;
