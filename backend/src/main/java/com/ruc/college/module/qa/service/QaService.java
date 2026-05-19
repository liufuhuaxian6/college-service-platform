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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QaService {

    private final QaKnowledgeMapper knowledgeMapper;
    private final QaDocumentMapper documentMapper;
    private final QaChatLogMapper chatLogMapper;
    private final AiProviderFactory aiProviderFactory;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    private static final long MAX_DOC_SIZE = 30L * 1024 * 1024;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9]{2,}");

    // ==================== 智能问答 ====================

    /**
     * 智能问答: 先匹配知识库 → 未命中则调 AI → 记录日志
     */
    public Map<String, Object> chat(String question) {
        Map<String, Object> result = new HashMap<>();

        // 1. 关键词匹配知识库
        List<String> tokens = extractTokens(question);
        List<QaKnowledge> matched = queryKnowledgeCandidates(question, tokens, 1);

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
            String context = buildKnowledgeContext(question, tokens);
            AiProvider ai = aiProviderFactory.getProvider();
            String aiAnswer = ai.chat(question, context);
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
        boolean hasCategory = StringUtils.hasText(category);
        boolean hasKeyword = StringUtils.hasText(keyword);
        LambdaQueryWrapper<QaKnowledge> wrapper = new LambdaQueryWrapper<QaKnowledge>()
                .eq(QaKnowledge::getStatus, 1)
                .eq(hasCategory, QaKnowledge::getCategory, category)
                .and(hasKeyword, w -> w.like(QaKnowledge::getQuestion, keyword).or().like(QaKnowledge::getKeywords, keyword))
                .orderByDesc(QaKnowledge::getCreatedAt);
        return knowledgeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public QaKnowledge getKnowledge(Long id) {
        QaKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) throw new BusinessException("知识条目不存在");
        return knowledge;
    }

    public Long addKnowledge(QaKnowledge knowledge) {
        if (knowledge == null) {
            throw new BusinessException("参数不能为空");
        }
        if (!StringUtils.hasText(knowledge.getQuestion())) {
            throw new BusinessException("标准问题不能为空");
        }
        if (!StringUtils.hasText(knowledge.getAnswer())) {
            throw new BusinessException("标准答案不能为空");
        }
        if (knowledge.getSortOrder() == null) {
            knowledge.setSortOrder(0);
        }
        knowledge.setCreatedBy(UserContext.getUserId());
        knowledge.setStatus(1);
        knowledgeMapper.insert(knowledge);
        return knowledge.getId();
    }

    public void updateKnowledge(Long id, QaKnowledge knowledge) {
        QaKnowledge existing = knowledgeMapper.selectById(id);
        if (existing == null) throw new BusinessException("知识条目不存在");
        if (knowledge == null) {
            throw new BusinessException("参数不能为空");
        }
        if (knowledge.getQuestion() != null && !StringUtils.hasText(knowledge.getQuestion())) {
            throw new BusinessException("标准问题不能为空");
        }
        if (knowledge.getAnswer() != null && !StringUtils.hasText(knowledge.getAnswer())) {
            throw new BusinessException("标准答案不能为空");
        }
        knowledge.setId(id);
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.updateById(knowledge);
    }

    public void deleteKnowledge(Long id) {
        knowledgeMapper.deleteById(id);
    }

    // ==================== 政策文档 ====================

    public List<QaDocument> getDocumentList(String category) {
        boolean hasCategory = StringUtils.hasText(category);
        return documentMapper.selectList(
                new LambdaQueryWrapper<QaDocument>()
                        .eq(QaDocument::getStatus, 1)
                        .eq(hasCategory, QaDocument::getCategory, category)
                        .orderByDesc(QaDocument::getCreatedAt)
        );
    }

    public Long addDocument(QaDocument doc) {
        if (doc == null) {
            throw new BusinessException("参数不能为空");
        }
        if (!StringUtils.hasText(doc.getTitle())) {
            throw new BusinessException("文档标题不能为空");
        }
        if (!StringUtils.hasText(doc.getFilePath())) {
            throw new BusinessException("文件路径不能为空");
        }
        if (doc.getFileSize() != null && doc.getFileSize() > MAX_DOC_SIZE) {
            throw new BusinessException("文件大小不能超过30MB");
        }
        doc.setCreatedBy(UserContext.getUserId());
        doc.setStatus(1);
        doc.setDownloadCount(0);
        documentMapper.insert(doc);
        return doc.getId();
    }

    public QaDocument getDocumentForDownload(Long id) {
        QaDocument doc = documentMapper.selectById(id);
        if (doc == null) throw new BusinessException("文档不存在");
        if (doc.getStatus() == null || doc.getStatus() != 1) {
            throw new BusinessException("文档已下架");
        }
        if (!StringUtils.hasText(doc.getFilePath())) {
            throw new BusinessException("文档文件路径为空");
        }
        // 下载计数 +1
        doc.setDownloadCount(doc.getDownloadCount() + 1);
        documentMapper.updateById(doc);
        return doc;
    }

    public void deleteDocument(Long id) {
        QaDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            return;
        }

        if (StringUtils.hasText(doc.getFilePath())) {
            String cleanPath = StringUtils.cleanPath(doc.getFilePath());
            String uploadPathPrefix = normalizeRelativePath(uploadPath);
            boolean inUploadDir = StringUtils.hasText(uploadPathPrefix)
                    && (cleanPath.startsWith(uploadPathPrefix + "/") || cleanPath.startsWith(uploadPathPrefix + "\\"));
            boolean suspicious = cleanPath.contains("..")
                    || cleanPath.contains(":")
                    || cleanPath.startsWith("/")
                    || cleanPath.startsWith("\\");

            if (inUploadDir && !suspicious) {
                File file = new File(System.getProperty("user.dir"), cleanPath);
                if (file.exists() && file.isFile()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        throw new BusinessException("删除文件失败，请重试");
                    }
                    File parent = file.getParentFile();
                    if (parent != null && parent.isDirectory()) {
                        String[] children = parent.list();
                        if (children != null && children.length == 0) {
                            parent.delete();
                        }
                    }
                }
            }
        }

        documentMapper.deleteById(id);
    }

    private List<String> extractTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String trimmed = text.trim();
        Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (StringUtils.hasText(token)) {
                tokens.add(token);
            }
        }
        if (trimmed.length() <= 50) {
            tokens.add(trimmed);
        }
        return tokens.stream().limit(8).toList();
    }

    private static String normalizeRelativePath(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim() : "uploads";
        value = value.replace("\\", "/");
        if (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!StringUtils.hasText(value)) {
            value = "uploads";
        }
        return value;
    }


    private List<QaKnowledge> queryKnowledgeCandidates(String question, List<String> tokens, int limit) {
        LambdaQueryWrapper<QaKnowledge> wrapper = new LambdaQueryWrapper<QaKnowledge>()
                .eq(QaKnowledge::getStatus, 1);
        if (tokens == null || tokens.isEmpty()) {
            wrapper.and(w -> w.like(QaKnowledge::getQuestion, question).or().like(QaKnowledge::getKeywords, question));
        } else {
            wrapper.and(w -> {
                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i);
                    if (i > 0) {
                        w.or();
                    }
                    w.like(QaKnowledge::getKeywords, token).or().like(QaKnowledge::getQuestion, token);
                }
            });
        }
        wrapper.orderByDesc(QaKnowledge::getSortOrder).orderByDesc(QaKnowledge::getCreatedAt).last("LIMIT " + Math.max(1, limit));
        return knowledgeMapper.selectList(wrapper);
    }

    private String buildKnowledgeContext(String question, List<String> tokens) {
        List<QaKnowledge> candidates = queryKnowledgeCandidates(question, tokens, 3);
        if (candidates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            QaKnowledge k = candidates.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("问题: ").append(k.getQuestion() == null ? "" : k.getQuestion()).append("\n");
            sb.append("答案: ").append(k.getAnswer() == null ? "" : k.getAnswer()).append("\n");
            if (StringUtils.hasText(k.getSourceUrl())) {
                sb.append("链接: ").append(k.getSourceUrl());
            }
        }
        return sb.toString();
    }
}
