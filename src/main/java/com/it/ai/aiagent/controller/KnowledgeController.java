package com.it.ai.aiagent.controller;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.it.ai.aiagent.store.TopicRepository;
import com.it.ai.aiagent.bean.Topic;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "知识库管理")
@RestController
@RequestMapping("/knowledge")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class KnowledgeController {
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> pineconeEmbeddingStore;

    @Autowired
    private TopicRepository topicRepository;

    @Operation(summary = "获取所有已上传的主题")
    @GetMapping("/topics")
    public TopicPageResult getTopics(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeSize,
                Sort.by(
                        Sort.Order.desc("updatedAt"),
                        Sort.Order.asc("name")
                )
        );

        Page<TopicRepository.TopicSummaryProjection> topicPage = topicRepository.findTopicSummaries(pageable);
        List<TopicSummary> items = new ArrayList<>();
        for (TopicRepository.TopicSummaryProjection topic : topicPage.getContent()) {
            items.add(new TopicSummary(topic.getId(), topic.getName(), topic.getDocCount()));
        }
        return new TopicPageResult(items, safePage, safeSize, topicPage.hasNext());
    }

    @Operation(summary = "上传文档到向量数据库")
    // consumes 指定了客户端在发送请求的时候，HTTPS头必须接受什么类型的格式，这里意味着这个接口表单只接受文件上传的数据
    // - produces 指定接口返回的数据类型
    // - params 请求中必须包含某种参数
    // - headers 请求头中必须包含某种请求头
    // - name 给这个映射添加一个名字
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("topic") String topic) {
        
        Map<String, Object> result = new HashMap<>();
        try (InputStream inputStream = file.getInputStream()) {
            
            // 1. 解析文件为 Document (支持 TXT, PDF, Word 等)
            DocumentParser documentParser = new ApacheTikaDocumentParser();
            Document document = documentParser.parse(inputStream);
            
            // 增加元数据：主题 (后续可根据主题过滤或者让 AI 参考)
            document.metadata().put("topic", topic);

            // 2. 配置摄入器 (Ingestor)
            // 策略调整：对于 Q&A 这种问答式结构，如果回答很长，500字很容易把一个完整的 Q&A 从中间腰斩。
            // 因此我们将 Chunk Size 扩大到 1200，Overlap 扩大到 100，确保大部分的一问一答能被完整包裹在一个 Segment 里
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    // 文档分割器，大模型的上下文窗口有限，将大文档切分成小块
                    //recursive 递归切分 ；1200 表示每个分片的大小 ；100表示两个相邻的分片之间重复的字符数
                    .documentSplitter(DocumentSplitters.recursive(1200, 100))
                    // 向量化模型
                    .embeddingModel(embeddingModel)
                    // 向量存储库
                    .embeddingStore(pineconeEmbeddingStore)
                    .build();

            // 3. 执行切分、向量化、入库
            ingestor.ingest(document);

            // mongoDB这里存储topic的数据，然后进行计数的工作
            Topic t = topicRepository.findByName(topic);
            Instant now = Instant.now();
            if (t == null) {
                t = new Topic();
                t.setName(topic);
                t.setDocCount(1);
                t.setCreatedAt(now);
            } else {
                t.setDocCount(t.getDocCount() + 1);
            }
            t.setUpdatedAt(now);
            topicRepository.save(t);
            
            result.put("status", "success");
            result.put("message", "文档上传并向量化成功！主题：" + topic);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    public record TopicSummary(String id, String name, int docCount) {
    }

    public record TopicPageResult(List<TopicSummary> items, int page, int size, boolean hasNext) {
    }
}
