package com.ruc.college.module.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.qa.entity.QaChatLog;
import com.ruc.college.module.qa.entity.QaDocumentChunk;
import com.ruc.college.module.qa.entity.QaDocument;
import com.ruc.college.module.qa.entity.QaKnowledge;
import com.ruc.college.module.qa.mapper.QaChatLogMapper;
import com.ruc.college.module.qa.mapper.QaDocumentMapper;
import com.ruc.college.module.qa.mapper.QaKnowledgeMapper;
import com.ruc.college.module.qa.service.ai.AiProvider;
import com.ruc.college.module.qa.service.ai.AiProviderFactory;
import com.ruc.college.module.qa.service.rag.DocumentRagService;
import com.ruc.college.module.qa.service.rag.RagScoringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QaService {

    private final QaKnowledgeMapper knowledgeMapper;
    private final QaDocumentMapper documentMapper;
    private final QaChatLogMapper chatLogMapper;
    private final AiProviderFactory aiProviderFactory;
    private final DocumentRagService documentRagService;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    /**
     * 抽取式回答最低置信度。低于此分数则视为"语义不相关"，返回兜底语而非伪造依据。
     * 计算口径见 {@link #extractiveScore(String, QaDocumentChunk)}: vector_score*100 + 关键词/意图/受众加权
     */
    @Value("${rag.extractive-confidence:60}")
    private int extractiveConfidence;

    private static final long MAX_DOC_SIZE = 30L * 1024 * 1024;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9]{2,}");
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("第[一二三四五六七八九十百千万零〇0-9]+条");

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
            // 2. RAG 检索文档向量片段 + 知识库候选，作为 AI 上下文
            List<QaDocumentChunk> ragChunks = documentRagService.retrieve(question, null);
            String ragContext = documentRagService.buildContext(ragChunks);
            String knowledgeContext = buildKnowledgeContext(question, tokens);
            String context = buildRagPromptContext(knowledgeContext, ragContext);
            AiProvider ai = aiProviderFactory.getProvider();
            boolean hasRagContext = StringUtils.hasText(ragContext);
            String aiAnswer = hasRagContext && ai.getName().equals("none")
                    ? buildExtractiveRagAnswer(question, ragChunks)
                    : ai.chat(question, context);
            result.put("answer", aiAnswer);
            result.put("sourceType", hasRagContext ? "rag" : (ai.getName().equals("none") ? "manual" : "ai"));
            result.put("sourceUrl", null);
            result.put("aiGenerated", !ai.getName().equals("none"));
            result.put("ragUsed", hasRagContext);
            log.setAnswer(aiAnswer);
            log.setSourceType(hasRagContext ? "rag" : (ai.getName().equals("none") ? "manual" : "ai"));
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

    public Map<String, Object> indexDocument(Long id) {
        return documentRagService.indexDocument(id);
    }

    public List<QaDocumentChunk> retrieveDocumentChunks(String question, String category) {
        return documentRagService.retrieve(question, category);
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

    private static final int MAX_EXTRACTIVE_CHUNKS = 3;
    private static final int MIN_SECONDARY_SCORE_RATIO = 60; // 次级 chunk 至少要达到最佳分数 60% 才纳入

    private String buildExtractiveRagAnswer(String question, List<QaDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "未在现有政策文件中找到明确依据，请联系辅导员确认。";
        }
        String q = question == null ? "" : question;
        List<QaDocumentChunk> ranked = chunks.stream()
                .sorted((a, b) -> Integer.compare(extractiveScore(q, b), extractiveScore(q, a)))
                .toList();
        int bestScore = extractiveScore(q, ranked.get(0));

        // 置信度门槛: 即使有候选, 最佳分数不达标也视为不相关, 避免给无关问题伪造依据
        if (bestScore < extractiveConfidence) {
            return "未在现有政策文件中找到明确依据，请联系辅导员确认。";
        }
        int threshold = Math.max(1, bestScore * MIN_SECONDARY_SCORE_RATIO / 100);

        StringBuilder sb = new StringBuilder();
        java.util.LinkedHashSet<String> seenTitles = new java.util.LinkedHashSet<>();
        int included = 0;
        for (QaDocumentChunk chunk : ranked) {
            if (included >= MAX_EXTRACTIVE_CHUNKS) break;
            int score = extractiveScore(q, chunk);
            if (included > 0 && score < threshold) break;

            String title = StringUtils.hasText(chunk.getTitle()) ? chunk.getTitle() : "政策文档";
            String content = chunk.getContent() == null ? "" : chunk.getContent().trim();
            content = focusExtractiveContent(q, content);
            if (!StringUtils.hasText(content)) continue;

            if (included > 0) sb.append("\n\n");
            sb.append("【依据 ").append(included + 1).append("】《").append(title).append("》\n").append(content);
            seenTitles.add(title);
            included++;
        }
        return sb.length() == 0 ? "未在现有政策文件中找到明确依据，请联系辅导员确认。" : sb.toString();
    }

    /**
     * 围绕最佳命中位置抽取一段连贯文本：
     * 1) 优先按"第X条"边界完整返回整条条款（不截断）
     * 2) 否则按段落起始定位，并允许最长 1200 字（条款式政策文件常见单条长度）
     */
    private static String focusExtractiveContent(String question, String content) {
        if (!StringUtils.hasText(content)) return "";

        int bestPosition = findBestMatchPosition(question, content);
        int sectionStart = findSectionStart(content, bestPosition);

        boolean atArticleBoundary = sectionStart < bestPosition
                && ARTICLE_PATTERN.matcher(content.substring(sectionStart, Math.min(content.length(), sectionStart + 8))).find();
        if (atArticleBoundary) {
            int sectionEnd = findNextArticleStart(content, sectionStart + 1);
            return content.substring(sectionStart, sectionEnd).trim();
        }

        int maxLength = sectionStart < bestPosition ? 900 : 520;
        String focused = content.substring(Math.min(sectionStart, content.length()));
        if (focused.length() > maxLength) {
            focused = focused.substring(0, maxLength) + "...";
        }
        return focused;
    }

    private static int findNextArticleStart(String content, int from) {
        Matcher matcher = ARTICLE_PATTERN.matcher(content);
        if (matcher.find(from)) {
            return matcher.start();
        }
        return content.length();
    }

    private static int extractiveScore(String question, QaDocumentChunk chunk) {
        String title = chunk.getTitle() == null ? "" : chunk.getTitle();
        String category = chunk.getCategory() == null ? "" : chunk.getCategory();
        String keywords = chunk.getKeywords() == null ? "" : chunk.getKeywords();
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        Set<String> queryTerms = RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(question));
        Set<String> contentTerms = RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(content));
        Set<String> metadataTerms = RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(title + "\n" + category + "\n" + keywords));

        double vectorScore = chunk.getScore() == null ? 0 : chunk.getScore();
        double score = vectorScore * 100;
        score += RagScoringUtil.weightedOverlap(queryTerms, contentTerms) * 55;
        score += RagScoringUtil.weightedOverlap(queryTerms, metadataTerms) * 25;
        score += RagScoringUtil.intentStructureScore(question, content) * 20;
        score += RagScoringUtil.audienceScopeScore(question, title) * 45;
        return (int) Math.round(score);
    }

    private static int findBestMatchPosition(String question, String content) {
        Set<String> queryTerms = RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(question));
        int bestPosition = 0;
        int bestScore = -1;
        for (String term : queryTerms) {
            if (term.length() < 2) {
                continue;
            }
            int position = content.indexOf(term);
            while (position >= 0) {
                int score = windowHitCount(content, queryTerms, position, 260);
                if (score > bestScore) {
                    bestScore = score;
                    bestPosition = position;
                }
                position = content.indexOf(term, position + term.length());
            }
        }
        return bestPosition;
    }

    private static int windowHitCount(String content, Set<String> terms, int center, int radius) {
        int from = Math.max(0, center - radius);
        int to = Math.min(content.length(), center + radius);
        String window = content.substring(from, to);
        int score = 0;
        for (String term : terms) {
            if (term.length() >= 2 && window.contains(term)) {
                score += term.length() >= 4 ? 2 : 1;
            }
        }
        return score;
    }

    private static int findSectionStart(String content, int bestPosition) {
        int start = Math.max(0, bestPosition);
        Matcher matcher = ARTICLE_PATTERN.matcher(content);
        while (matcher.find()) {
            if (matcher.start() <= bestPosition) {
                start = matcher.start();
            } else {
                break;
            }
        }
        if (start != bestPosition) {
            return start;
        }
        int paragraphStart = content.lastIndexOf('\n', Math.max(0, bestPosition - 1));
        return paragraphStart >= 0 ? paragraphStart + 1 : Math.max(0, bestPosition - 120);
    }

    private static String buildRagPromptContext(String knowledgeContext, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("回答规则：只能依据下列资料回答；资料中没有明确依据时，请回答“未在现有政策文件中找到明确依据，请联系辅导员确认。”；不得编造政策。\n");
        if (StringUtils.hasText(knowledgeContext)) {
            sb.append("\n【标准问答候选】\n").append(knowledgeContext);
        }
        if (StringUtils.hasText(ragContext)) {
            sb.append("\n\n【政策文档片段】\n").append(ragContext);
        }
        return sb.toString();
    }
}
