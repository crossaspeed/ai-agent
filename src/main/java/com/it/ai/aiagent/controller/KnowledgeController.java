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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.it.ai.aiagent.store.TopicRepository;
import com.it.ai.aiagent.bean.Topic;
import java.util.List;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "知识库管理")
@RestController
@RequestMapping("/knowledge")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class KnowledgeController {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> pineconeEmbeddingStore;

    @Autowired
    private TopicRepository topicRepository;

    @Operation(summary = "获取所有已上传的主题")
    @GetMapping("/topics")
    public List<Topic> getTopics() {
        return topicRepository.findAll();
    }

    @Operation(summary = "上传文档到向量数据库")
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
                    .documentSplitter(DocumentSplitters.recursive(1200, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(pineconeEmbeddingStore)
                    .build();

            // 3. 执行切分、向量化、入库
            ingestor.ingest(document);
            
            Topic t = topicRepository.findByName(topic);
            if (t == null) {
                t = new Topic();
                t.setName(topic);
                t.setDocCount(1);
            } else {
                t.setDocCount(t.getDocCount() + 1);
            }
            topicRepository.save(t);
            
            result.put("status", "success");
            result.put("message", "文档上传并向量化成功！主题：" + topic);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
