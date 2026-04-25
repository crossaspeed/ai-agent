package com.it.ai.aiagent.store;

import com.it.ai.aiagent.config.MemoryCompressionConfig;
import com.it.ai.aiagent.service.MemoryCompressionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 三层上下文压缩的ChatMemoryStore包装器。
 *
 * Layer 1 (摘要压缩): 当历史超过threshold条时，将较早的消息通过LLM总结为一段摘要
 * Layer 2 (Assistant裁剪): 对过长的AI消息进行截断
 * Layer 3 (滑动窗口兜底): 直接丢弃最早的消息确保不超过maxTotalMessages
 *
 * 压缩发生在updateMessages时，对MongoDB中的完整历史（而非仅20条窗口）应用三层压缩。
 */
@Component
public class CompressionChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(CompressionChatMemoryStore.class);
    private static final String SUMMARY_PREFIX = "[对话摘要] ";

    @Autowired
    private MongoChatMemoryStore mongoStore;

    @Autowired
    private MemoryCompressionService compressionService;

    @Autowired
    private MemoryCompressionConfig config;

    // Per-session locks for thread safety
    private final ConcurrentHashMap<Long, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    private ReentrantLock getLock(Object memoryId) {
        long id = ((Number) memoryId).longValue();
        return sessionLocks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ReentrantLock lock = getLock(memoryId);
        lock.lock();
        try {
            return mongoStore.getMessages(memoryId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ReentrantLock lock = getLock(memoryId);
        lock.lock();
        try {
            // 1. 获取MongoDB中的完整历史
            List<ChatMessage> fullHistory = mongoStore.getMessages(memoryId);

            // 2. 合并新旧消息
            List<ChatMessage> merged = mergeHistory(fullHistory, messages);

            // 3. 应用三层压缩
            List<ChatMessage> compressed = applyCompression(merged);

            // 4. 持久化
            mongoStore.updateMessages(memoryId, compressed);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        ReentrantLock lock = getLock(memoryId);
        lock.lock();
        try {
            mongoStore.deleteMessages(memoryId);
            sessionLocks.remove(((Number) memoryId).longValue());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 合并历史记录和新消息，处理边界重复问题。
     */
    private List<ChatMessage> mergeHistory(List<ChatMessage> existing, List<ChatMessage> newWindow) {
        if (existing == null || existing.isEmpty()) {
            return new ArrayList<>(Objects.requireNonNull(newWindow));
        }

        if (newWindow == null || newWindow.isEmpty()) {
            return new ArrayList<>(existing);
        }

        List<ChatMessage> result = new ArrayList<>(existing);

        // 检查边界是否有重复（避免重复添加相同的消息）
        if (!existing.isEmpty() && !newWindow.isEmpty()) {
            ChatMessage lastExisting = existing.get(existing.size() - 1);
            ChatMessage firstNew = newWindow.get(0);

            if (lastExisting.equals(firstNew)) {
                // 边界重复，跳过第一个新消息
                if (newWindow.size() > 1) {
                    result.addAll(newWindow.subList(1, newWindow.size()));
                }
            } else {
                result.addAll(newWindow);
            }
        }

        return result;
    }

    /**
     * 应用三层压缩策略。
     */
    private List<ChatMessage> applyCompression(List<ChatMessage> messages) {
        if (!config.isEnabled()) {
            return messages;
        }

        if (messages.isEmpty()) {
            return messages;
        }

        int beforeSize = messages.size();

        // Tier 1: 摘要压缩
        messages = applyTier1(messages);

        // Tier 2: AI消息裁剪
        messages = applyTier2(messages);

        // Tier 3: 滑动窗口兜底
        messages = applyTier3(messages);

        int afterSize = messages.size();
        if (beforeSize != afterSize) {
            log.info("压缩完成：sessionId={}, 压缩前={}条, 压缩后={}条",
                    "unknown", beforeSize, afterSize);
        }

        return messages;
    }

    /**
     * Tier 1: 摘要压缩
     * 当历史消息超过threshold时，将较早的消息通过LLM总结为一段摘要
     */
    private List<ChatMessage> applyTier1(List<ChatMessage> messages) {
        int threshold = config.getTier1().getThreshold();

        if (messages.size() <= threshold) {
            return messages;
        }

        // 检查是否已有摘要（防止重复压缩）
        boolean hasSummary = compressionService.hasSummary(messages);
        if (hasSummary) {
            return messages;
        }

        // 保留最近N条消息不参与摘要
        int preserveRecent = config.getTier1().getPreserveRecent();
        int preserveCount = Math.min(preserveRecent, messages.size());
        int summarizeCount = messages.size() - preserveCount;

        if (summarizeCount <= 0) {
            return messages;
        }

        List<ChatMessage> toSummarize = new ArrayList<>(messages.subList(0, summarizeCount));
        List<ChatMessage> toPreserve = new ArrayList<>(messages.subList(summarizeCount, messages.size()));

        // 生成摘要
        SystemMessage summary = compressionService.summarizeIfNeeded(toSummarize, preserveRecent);
        if (summary == null) {
            return messages;
        }

        // 重建消息列表：摘要在前，保留的消息在后
        List<ChatMessage> result = new ArrayList<>();
        result.add(summary);
        result.addAll(toPreserve);

        return result;
    }

    /**
     * Tier 2: AI消息裁剪
     * 当消息数量超过threshold时，对过长的AI消息进行截断
     */
    private List<ChatMessage> applyTier2(List<ChatMessage> messages) {
        int threshold = config.getTier2().getThreshold();

        if (messages.size() <= threshold) {
            return messages;
        }

        int maxLength = config.getTier2().getMaxAiMessageLength();
        List<ChatMessage> original = new ArrayList<>(messages);
        messages = compressionService.trimLongAiMessages(messages, maxLength);

        long trimmedCount = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof AiMessage && original.get(i) instanceof AiMessage) {
                String originalText = ((AiMessage) original.get(i)).text();
                String trimmedText = ((AiMessage) messages.get(i)).text();
                if (!originalText.equals(trimmedText)) {
                    trimmedCount++;
                }
            }
        }

        if (trimmedCount > 0) {
            log.info("Tier 2压缩：裁剪了{}条长AI消息", trimmedCount);
        }

        return messages;
    }

    /**
     * Tier 3: 滑动窗口兜底
     * 确保消息总数不超过maxTotalMessages，直接丢弃最早的消息
     */
    private List<ChatMessage> applyTier3(List<ChatMessage> messages) {
        int maxTotal = config.getTier3().getMaxTotalMessages();

        if (messages.size() <= maxTotal) {
            return messages;
        }

        // 分离摘要消息和普通消息
        List<ChatMessage> summaryMessages = messages.stream()
                .filter(m -> m instanceof SystemMessage
                        && ((SystemMessage) m).text().startsWith(SUMMARY_PREFIX))
                .collect(Collectors.toList());

        List<ChatMessage> normalMessages = messages.stream()
                .filter(m -> !(m instanceof SystemMessage
                        && ((SystemMessage) m).text().startsWith(SUMMARY_PREFIX)))
                .collect(Collectors.toList());

        int toDrop = messages.size() - maxTotal;

        if (toDrop >= normalMessages.size()) {
            // 至少保留一条非摘要消息
            toDrop = normalMessages.size() - 1;
            if (toDrop < 0) toDrop = 0;
        }

        List<ChatMessage> result = new ArrayList<>(summaryMessages);
        if (toDrop < normalMessages.size()) {
            result.addAll(normalMessages.subList(toDrop, normalMessages.size()));
        } else {
            result.addAll(normalMessages);
        }

        if (toDrop > 0) {
            log.info("Tier 3压缩：丢弃了{}条最早的消息", toDrop);
        }

        return result;
    }
}
