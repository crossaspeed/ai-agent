package com.it.ai.aiagent.store;

import com.it.ai.aiagent.bean.ChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MongoChatMemoryStore implements ChatMemoryStore {
    private static final int SESSION_TITLE_MAX_LENGTH = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String RAG_INJECTION_SEPARATOR = "\n\nAnswer using the following information:\n";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null) return new LinkedList<>();
        return ChatMessageDeserializer.messagesFromJson(chatMessages.getContent());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        Instant now = Instant.now();
        String title = buildSessionTitle(messages, memoryId);
        Update update = new Update();
        update.set("content", ChatMessageSerializer.messagesToJson(messages));
        update.set("updatedAt", now);
        update.setOnInsert("createdAt", now);
        update.setOnInsert("memoryId", memoryId);
        update.setOnInsert("title", title);
        //根据query条件能查询出文档，则修改文档；否则新增文档
        mongoTemplate.upsert(query, update, ChatMessages.class);

        // 历史数据可能缺少 title；只在为空时补写一次。
        Query missingTitleQuery = new Query(new Criteria().andOperator(
            Criteria.where("memoryId").is(memoryId),
            new Criteria().orOperator(
                Criteria.where("title").exists(false),
                Criteria.where("title").is(null),
                Criteria.where("title").is("")
            )
        ));
        mongoTemplate.updateFirst(missingTitleQuery, new Update().set("title", title), ChatMessages.class);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        mongoTemplate.remove(query, ChatMessages.class);
    }

    public SessionPageResult getSessionPage(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long skip = (long) (safePage - 1) * safeSize;

        Query query = new Query();
        query.fields().include("memoryId").include("title");
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("updatedAt"),
                org.springframework.data.domain.Sort.Order.desc("memoryId")
        ));
        query.skip(skip).limit(safeSize + 1);

        List<ChatMessages> sessions = mongoTemplate.find(query, ChatMessages.class);
        boolean hasNext = sessions.size() > safeSize;
        if (hasNext) {
            sessions = new ArrayList<>(sessions.subList(0, safeSize));
        }

        List<SessionSummary> items = sessions.stream()
                .map(session -> new SessionSummary(
                        session.getMemoryId(),
                        defaultTitle(session.getMemoryId(), session.getTitle())
                ))
                .collect(Collectors.toList());

        return new SessionPageResult(items, safePage, safeSize, hasNext);
    }

    private String buildSessionTitle(List<ChatMessage> messages, Object memoryId) {
        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message instanceof UserMessage) {
                    String userText = ((UserMessage) message).singleText();
                    String normalized = normalizeTitleText(userText);
                    if (StringUtils.hasText(normalized)) {
                        return truncateTitle(normalized);
                    }
                }
            }
        }
        return "新对话 " + Objects.toString(memoryId, "");
    }

    private String normalizeTitleText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text;
        int ragMarkerIndex = normalized.indexOf(RAG_INJECTION_SEPARATOR);
        if (ragMarkerIndex >= 0) {
            normalized = normalized.substring(0, ragMarkerIndex);
        }
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private String truncateTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return title;
        }
        if (title.length() <= SESSION_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, SESSION_TITLE_MAX_LENGTH) + "...";
    }

    private String defaultTitle(Long memoryId, String title) {
        if (StringUtils.hasText(title)) {
            return title;
        }
        return "新对话 " + Objects.toString(memoryId, "");
    }

    public record SessionSummary(Long memoryId, String title) {
    }

    public record SessionPageResult(List<SessionSummary> items, int page, int size, boolean hasNext) {
    }
}