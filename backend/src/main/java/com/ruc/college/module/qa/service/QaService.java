package com.ruc.college.module.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.qa.entity.QaChatLog;
import com.ruc.college.module.qa.entity.QaDocument;
import com.ruc.college.module.qa.entity.QaKnowledge;
import com.ruc.college.module.qa.mapper.QaChatLogMapper;
import com.ruc.college.module.qa.mapper.QaDocumentMapper;
import com.ruc.college.module.qa.mapper.QaKnowledgeMapper;
import com.ruc.college.module.qa.service.ai.AiProvider;
import com.ruc.college.module.qa.service.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QaService {

    private final QaKnowledgeMapper knowledgeMapper;
    private final QaDocumentMapper documentMapper;
    private final QaChatLogMapper chatLogMapper;
    private final AiProviderFactory aiProviderFactory;

    // ==================== 智能问答 ====================

    /**
     * 智能问答: 先匹配知识库 → 未命中则调 AI → 记录日志
     */
    public Map<String, Object> chat(String question) {
        Map<String, Object> result = new HashMap<>();

        // 1. 关键词匹配知识库
        List<QaKnowledge> matched = knowledgeMapper.selectList(
                new LambdaQueryWrapper<QaKnowledge>()
                        .eq(QaKnowledge::getStatus, 1)
                        .and(w -> w
                                .like(QaKnowledge::getKeywords, question)
                                .or()
                                .like(QaKnowledge::getQuestion, question)
                        )
                        .orderByDesc(QaKnowledge::getSortOrder)
                        .last("LIMIT 1")
        );

        QaChatLog log = new QaChatLog();
        log.setUserId(UserContext.getUserId());
        log.setQuestion(question);

        if (!matched.isEmpty()) {
            QaKnowledge knowledge = matched.get(0);
            result.put("answer", knowledge.getAnswer());
            result.put("sourceType", "knowledge");
            result.put("sourceUrl", knowledge.getSourceUrl());
            result.put("aiGenerated", false);
            log.setAnswer(knowledge.getAnswer());
            log.setSourceType("knowledge");
            log.setMatched(true);
        } else {
            // 2. 调用 AI
            AiProvider ai = aiProviderFactory.getProvider();
            String aiAnswer = ai.chat(question, "");
            result.put("answer", aiAnswer);
            result.put("sourceType", ai.getName().equals("none") ? "manual" : "ai");
            result.put("sourceUrl", null);
            result.put("aiGenerated", !ai.getName().equals("none"));
            log.setAnswer(aiAnswer);
            log.setSourceType(ai.getName().equals("none") ? "manual" : "ai");
            log.setMatched(false);
        }

        // 3. 记录问答日志
        chatLogMapper.insert(log);

        return result;
    }

    public Page<QaChatLog> getChatHistory(int page, int size) {
        return chatLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<QaChatLog>()
                        .eq(QaChatLog::getUserId, UserContext.getUserId())
                        .orderByDesc(QaChatLog::getCreatedAt)
        );
    }

    // ==================== 知识库管理 ====================

    public Page<QaKnowledge> getKnowledgePage(int page, int size, String category, String keyword) {
        LambdaQueryWrapper<QaKnowledge> wrapper = new LambdaQueryWrapper<QaKnowledge>()
                .eq(QaKnowledge::getStatus, 1)
                .eq(category != null, QaKnowledge::getCategory, category)
                .and(keyword != null, w -> w
                        .like(QaKnowledge::getQuestion, keyword)
                        .or()
                        .like(QaKnowledge::getKeywords, keyword)
                )
                .orderByDesc(QaKnowledge::getCreatedAt);
        return knowledgeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public QaKnowledge getKnowledge(Long id) {
        QaKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) throw new BusinessException("知识条目不存在");
        return knowledge;
    }

    public Long addKnowledge(QaKnowledge knowledge) {
        knowledge.setCreatedBy(UserContext.getUserId());
        knowledge.setStatus(1);
        knowledgeMapper.insert(knowledge);
        return knowledge.getId();
    }

    public void updateKnowledge(Long id, QaKnowledge knowledge) {
        QaKnowledge existing = knowledgeMapper.selectById(id);
        if (existing == null) throw new BusinessException("知识条目不存在");
        knowledge.setId(id);
        knowledgeMapper.updateById(knowledge);
    }

    public void deleteKnowledge(Long id) {
        knowledgeMapper.deleteById(id);
    }

    // ==================== 政策文档 ====================

    public List<QaDocument> getDocumentList(String category) {
        return documentMapper.selectList(
                new LambdaQueryWrapper<QaDocument>()
                        .eq(QaDocument::getStatus, 1)
                        .eq(category != null, QaDocument::getCategory, category)
                        .orderByDesc(QaDocument::getCreatedAt)
        );
    }

    public Long addDocument(QaDocument doc) {
        doc.setCreatedBy(UserContext.getUserId());
        doc.setStatus(1);
        doc.setDownloadCount(0);
        documentMapper.insert(doc);
        return doc.getId();
    }

    public QaDocument getDocumentForDownload(Long id) {
        QaDocument doc = documentMapper.selectById(id);
        if (doc == null) throw new BusinessException("文档不存在");
        // 下载计数 +1
        doc.setDownloadCount(doc.getDownloadCount() + 1);
        documentMapper.updateById(doc);
        return doc;
    }

    public void deleteDocument(Long id) {
        documentMapper.deleteById(id);
    }
}
