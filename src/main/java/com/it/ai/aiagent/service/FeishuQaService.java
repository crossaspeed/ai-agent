package com.it.ai.aiagent.service;

import com.it.ai.aiagent.assistant.XiaocAgent;
import dev.langchain4j.service.TokenStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

@Service
public class FeishuQaService {

    @Autowired
    private XiaocAgent xiaocAgent;

    @Value("${feishu.qa.timeout-seconds:45}")
    private long qaTimeoutSeconds;

    public QaProcessResult processQa(String openId, String userText) {
        if (!StringUtils.hasText(userText)) {
            return QaProcessResult.ignored("未识别到有效问答内容");
        }

        long memoryId = resolveMemoryId(openId);
        String prompt = userText.trim();
        StringBuilder answerBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        // 异步的去询问ai
        try {
            TokenStream tokenStream = xiaocAgent.chat(memoryId, prompt);
            tokenStream.onPartialResponse(token -> {
                        if (token != null) {
                            answerBuilder.append(token);
                        }
                    })
                    .onCompleteResponse(response -> latch.countDown())
                    .onError(error -> {
                        errorRef.set(error);
                        latch.countDown();
                    })
                    .start();
            // 主线程执行到latch.await时会进行阻塞操作，直到计数器变为0
            // 强制超时判断，留给AI足够的生成时间
            boolean finished = latch.await(Math.max(5, qaTimeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                return QaProcessResult.failed("知识问答超时，请稍后重试或换个问法。");
            }
            if (errorRef.get() != null) {
                return QaProcessResult.failed("知识问答失败，请稍后重试。");
            }
            // 将ai的回答返回给飞书机器人
            String answer = answerBuilder.toString().trim();
            if (!StringUtils.hasText(answer)) {
                return QaProcessResult.failed("当前未生成有效回答，请换个主题或换种问法再试试。");
            }
            return QaProcessResult.success(truncate(answer, 1800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return QaProcessResult.failed("知识问答被中断，请稍后重试。");
        } catch (Exception e) {
            return QaProcessResult.failed("知识问答失败，请稍后重试。");
        }
    }

    /**
     * 将字符串标识符“缩水”并“转型”为一个大数字
     * @param openId
     * @return
     */
    private long resolveMemoryId(String openId) {
        if (!StringUtils.hasText(openId)) {
            return 10_000_001L;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(openId.getBytes(StandardCharsets.UTF_8));
        return 10_000_000L + crc32.getValue();
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...(内容过长，已截断)";
    }

    public record QaProcessResult(boolean handled, boolean success, String message) {
        public static QaProcessResult ignored(String message) {
            return new QaProcessResult(false, false, message);
        }

        public static QaProcessResult success(String message) {
            return new QaProcessResult(true, true, message);
        }

        public static QaProcessResult failed(String message) {
            return new QaProcessResult(true, false, message);
        }
    }
}