package com.it.ai.aiagent.service;

import com.it.ai.aiagent.assistant.SummaryPromptAgent;
import com.it.ai.aiagent.config.MemoryCompressionConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemoryCompressionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCompressionService.class);
    private static final String SUMMARY_PREFIX = "[对话摘要] ";

    @Autowired
    private SummaryPromptAgent summaryPromptAgent;

    @Autowired
    private MemoryCompressionConfig config;

    /**
     * 摘要压缩：当历史消息超过阈值时，将较早的消息通过LLM总结为一段摘要。
     *
     * @param allMessages 完整消息历史
     * @param preserveRecent 保留最近N条消息不参与摘要
     * @return 摘要SystemMessage，如果不需要压缩则返回null
     */
    public SystemMessage summarizeIfNeeded(List<ChatMessage> allMessages, int preserveRecent) {
        if (!config.isEnabled() || allMessages.size() <= config.getTier1().getThreshold()) {
            return null;
        }

        // 检查是否已有摘要（防止重复压缩）
        boolean hasSummary = allMessages.stream()
                .anyMatch(m -> m instanceof SystemMessage
                        && ((SystemMessage) m).text().startsWith(SUMMARY_PREFIX));
        if (hasSummary) {
            return null;
        }

        int threshold = config.getTier1().getThreshold();
        int toPreserve = config.getTier1().getPreserveRecent();

        if (allMessages.size() <= threshold) {
            return null;
        }

        // 分离要摘要的消息和要保留的消息
        int preserveCount = Math.min(toPreserve, allMessages.size());
        int summarizeCount = allMessages.size() - preserveCount;

        if (summarizeCount <= 0) {
            return null;
        }

        List<ChatMessage> toSummarize = allMessages.subList(0, summarizeCount);
        List<ChatMessage> toPreserveList = allMessages.subList(summarizeCount, allMessages.size());

        // 检查要摘要的消息中是否有非系统消息
        boolean hasContentToSummarize = toSummarize.stream()
                .anyMatch(m -> !(m instanceof SystemMessage));
        if (!hasContentToSummarize) {
            return null;
        }

        String historyText = serializeForSummary(toSummarize);

        try {
            String summary = summaryPromptAgent.summarize(historyText);
            if (summary == null || summary.isBlank()) {
                log.warn("LLM返回空摘要，跳过压缩");
                return null;
            }

            // 截断超过最大长度的摘要
            int maxLength = config.getTier1().getMaxSummaryLength();
            if (summary.length() > maxLength) {
                summary = summary.substring(0, maxLength) + "...";
            }

            log.info("Tier 1摘要压缩：{}条消息压缩为{}字摘要", summarizeCount, summary.length());
            return SystemMessage.from(SUMMARY_PREFIX + summary);
        } catch (Exception e) {
            log.error("LLM摘要生成失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * AI消息裁剪（Tier 2）：对超过最大长度的AI消息进行截断。
     *
     * @param messages 消息列表
     * @param maxLength 最大字符数
     * @return 裁剪后的消息列表
     */
    public List<ChatMessage> trimLongAiMessages(List<ChatMessage> messages, int maxLength) {
        return messages.stream()
                .map(msg -> {
                    if (msg instanceof AiMessage) {
                        String text = ((AiMessage) msg).text();
                        if (text != null && text.length() > maxLength) {
                            String trimmed = text.substring(0, maxLength) + "...[已截断]";
                            log.info("Tier 2裁剪：AI消息从{}字截断至{}字", text.length(), maxLength);
                            return AiMessage.from(trimmed);
                        }
                    }
                    return msg;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将消息列表序列化为可读文本，用于LLM摘要生成。
     */
    public String serializeForSummary(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String role;
            if (msg.type() == dev.langchain4j.data.message.ChatMessageType.USER) {
                role = "用户";
            } else if (msg.type() == dev.langchain4j.data.message.ChatMessageType.AI) {
                role = "AI助手";
            } else if (msg.type() == dev.langchain4j.data.message.ChatMessageType.SYSTEM) {
                role = "系统";
            } else {
                role = "其他";
            }

            String content;
            if (msg instanceof UserMessage) {
                content = ((UserMessage) msg).singleText();
            } else if (msg instanceof AiMessage) {
                content = ((AiMessage) msg).text();
            } else if (msg instanceof SystemMessage) {
                content = ((SystemMessage) msg).text();
            } else {
                content = msg.toString();
            }

            sb.append(String.format("%d. [%s]: %s\n", i + 1, role, content));
        }
        return sb.toString();
    }

    /**
     * 检查消息列表中是否已存在摘要。
     */
    public boolean hasSummary(List<ChatMessage> messages) {
        return messages.stream()
                .anyMatch(m -> m instanceof SystemMessage
                        && ((SystemMessage) m).text().startsWith(SUMMARY_PREFIX));
    }

    /**
     * 从消息列表中提取摘要文本（不含前缀）。
     */
    public String extractSummaryText(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof SystemMessage
                        && ((SystemMessage) m).text().startsWith(SUMMARY_PREFIX))
                .map(m -> ((SystemMessage) m).text().substring(SUMMARY_PREFIX.length()))
                .findFirst()
                .orElse("");
    }
}
