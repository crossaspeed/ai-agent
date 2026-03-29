package com.it.ai.aiagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import io.pinecone.clients.Pinecone;
import org.openapitools.db_control.client.model.IndexModel;
import org.openapitools.db_control.client.model.DeletionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class PineconeConfig {
    
    private static final Logger log = LoggerFactory.getLogger(PineconeConfig.class);

    @Value("${pinecone.api.key}")
    private String pineconeApiKey;

    @Value("${pinecone.index.name}")
    private String pineconeIndex;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String embeddingApiKey;

    @Value("${langchain4j.open-ai.embedding-model.base-url}")
    private String embeddingBaseUrl;

    @Bean
    @org.springframework.context.annotation.Primary
    public dev.langchain4j.model.embedding.EmbeddingModel embeddingModel() {
        return dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .modelName("text-embedding-3-small")
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> pineconeEmbeddingStore() {
        if (!StringUtils.hasText(pineconeApiKey)) {
            log.warn("未配置 Pinecone API Key，已降级为内存向量库(InMemoryEmbeddingStore)。");
            return new InMemoryEmbeddingStore<>();
        }

        try {
            Pinecone pc = new Pinecone.Builder(pineconeApiKey).build();
            List<IndexModel> indexes = pc.listIndexes().getIndexes();
            
            boolean indexExists = false;
            if (indexes != null) {
                indexExists = indexes.stream().anyMatch(idx -> idx.getName().equals(pineconeIndex));
            }

            if (!indexExists) {
                log.info("未发现 Pinecone 索引 '{}'，正在尝试自动创建(基于Serverless, aws us-east-1, 1536维度)...", pineconeIndex);
                pc.createServerlessIndex(
                    pineconeIndex,
                    "cosine",
                    1536,
                    "aws",
                    "us-east-1",
                    DeletionProtection.DISABLED
                );
                log.info("Pinecone 索引 '{}' 创建指令已发送！正在等待几秒钟以确保其 Ready 状态...", pineconeIndex);
                Thread.sleep(10000); 
                log.info("等待结束，尝试连接索引...");
            } else {
                log.info("发现已存在的 Pinecone 索引 '{}'，跳过创建步骤。", pineconeIndex);
            }
            return PineconeEmbeddingStore.builder()
                    .apiKey(pineconeApiKey)
                    .index(pineconeIndex)
                    .build();
        } catch (Exception e) {
            log.error("Pinecone 初始化失败，已降级为内存向量库(InMemoryEmbeddingStore): {}", e.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }

    @Bean
    public ContentRetriever pineconeContentRetriever(
            EmbeddingStore<TextSegment> pineconeEmbeddingStore,
            dev.langchain4j.model.embedding.EmbeddingModel embeddingModel) {
        
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(pineconeEmbeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3) // 每次检索前 3 个最相关的段落
                .minScore(0.7) // 相似度阈值，过滤掉不相关的结果
                .build();
    }
}
