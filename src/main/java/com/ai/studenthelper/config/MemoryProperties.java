package com.ai.studenthelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S4 / T-401 长期记忆文件化配置（技术规范 5.10）。
 * 所有阈值都有默认值，关闭态不读不写，配置只在 memory 开关开启时生效。
 */
@Component
@ConfigurationProperties(prefix = "studenthelper.memory")
public class MemoryProperties {

    /** 记忆根目录，与 data/images 同级。 */
    private String dir = "./data/memory";

    /** MEMORY.md 注入 system prompt 的字符上限（AC-401-2）。 */
    private int injectMaxChars = 2000;

    /** 对话压缩触发阈值：历史消息条数（AC-401-4 / AC-401-8）。 */
    private int compactionTriggerMessages = 30;

    /** 对话压缩触发阈值：估算 token 数，与条数阈值取 OR（AC-401-9，TCR-040）。 */
    private int compactionTriggerTokens = 12000;

    /** 压缩后保留的最近消息条数。 */
    private int compactionKeepMessages = 10;

    /** Consolidation 每日最多执行次数（AC-401-5）。 */
    private int consolidationMaxPerDay = 3;

    /** 两次成功 Consolidation 的最小间隔小时数，避免连续整点用完当日次数。 */
    private int consolidationMinGapHours = 6;

    /** Consolidation 读取最近多少天的 L1。 */
    private int consolidationLookbackDays = 7;

    /** L1 归档天数，超过后移动到 archive/。 */
    private int dailyRetentionDays = 90;

    /** 单条记忆最大字符数，避免模型一次写入过长内容。 */
    private int maxEntryChars = 500;

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
    public int getInjectMaxChars() { return injectMaxChars; }
    public void setInjectMaxChars(int injectMaxChars) { this.injectMaxChars = injectMaxChars; }
    public int getCompactionTriggerMessages() { return compactionTriggerMessages; }
    public void setCompactionTriggerMessages(int compactionTriggerMessages) { this.compactionTriggerMessages = compactionTriggerMessages; }
    public int getCompactionTriggerTokens() { return compactionTriggerTokens; }
    public void setCompactionTriggerTokens(int compactionTriggerTokens) { this.compactionTriggerTokens = compactionTriggerTokens; }
    public int getCompactionKeepMessages() { return compactionKeepMessages; }
    public void setCompactionKeepMessages(int compactionKeepMessages) { this.compactionKeepMessages = compactionKeepMessages; }
    public int getConsolidationMaxPerDay() { return consolidationMaxPerDay; }
    public void setConsolidationMaxPerDay(int consolidationMaxPerDay) { this.consolidationMaxPerDay = consolidationMaxPerDay; }
    public int getConsolidationMinGapHours() { return consolidationMinGapHours; }
    public void setConsolidationMinGapHours(int consolidationMinGapHours) { this.consolidationMinGapHours = consolidationMinGapHours; }
    public int getConsolidationLookbackDays() { return consolidationLookbackDays; }
    public void setConsolidationLookbackDays(int consolidationLookbackDays) { this.consolidationLookbackDays = consolidationLookbackDays; }
    public int getDailyRetentionDays() { return dailyRetentionDays; }
    public void setDailyRetentionDays(int dailyRetentionDays) { this.dailyRetentionDays = dailyRetentionDays; }
    public int getMaxEntryChars() { return maxEntryChars; }
    public void setMaxEntryChars(int maxEntryChars) { this.maxEntryChars = maxEntryChars; }
}
