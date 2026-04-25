package com.it.ai.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "memory.compression")
public class MemoryCompressionConfig {

    private boolean enabled = true;
    private Tier1Config tier1 = new Tier1Config();
    private Tier2Config tier2 = new Tier2Config();
    private Tier3Config tier3 = new Tier3Config();
    private boolean async = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Tier1Config getTier1() {
        return tier1;
    }

    public void setTier1(Tier1Config tier1) {
        this.tier1 = tier1;
    }

    public Tier2Config getTier2() {
        return tier2;
    }

    public void setTier2(Tier2Config tier2) {
        this.tier2 = tier2;
    }

    public Tier3Config getTier3() {
        return tier3;
    }

    public void setTier3(Tier3Config tier3) {
        this.tier3 = tier3;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public static class Tier1Config {
        /**
         * 触发摘要压缩的消息数量阈值
         * 当历史消息超过此数量时，触发Tier 1摘要压缩
         */
        private int threshold = 12;

        /**
         * 保留最近N条消息不参与摘要压缩
         */
        private int preserveRecent = 8;

        /**
         * 摘要最大字符数（汉字）
         */
        private int maxSummaryLength = 300;

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public int getPreserveRecent() {
            return preserveRecent;
        }

        public void setPreserveRecent(int preserveRecent) {
            this.preserveRecent = preserveRecent;
        }

        public int getMaxSummaryLength() {
            return maxSummaryLength;
        }

        public void setMaxSummaryLength(int maxSummaryLength) {
            this.maxSummaryLength = maxSummaryLength;
        }
    }

    public static class Tier2Config {
        /**
         * 触发AI消息裁剪的消息数量阈值
         * 当经过Tier 1后消息仍超过此数量时，触发Tier 2裁剪
         */
        private int threshold = 18;

        /**
         * AI消息最大字符数，超过此长度将被截断
         */
        private int maxAiMessageLength = 500;

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public int getMaxAiMessageLength() {
            return maxAiMessageLength;
        }

        public void setMaxAiMessageLength(int maxAiMessageLength) {
            this.maxAiMessageLength = maxAiMessageLength;
        }
    }

    public static class Tier3Config {
        /**
         * 滑动窗口兜底的最大消息数量
         * 经过Tier 1和Tier 2后，消息总数不得超过此值
         */
        private int maxTotalMessages = 25;

        public int getMaxTotalMessages() {
            return maxTotalMessages;
        }

        public void setMaxTotalMessages(int maxTotalMessages) {
            this.maxTotalMessages = maxTotalMessages;
        }
    }
}
