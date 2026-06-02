package com.ruc.college.module.qa.service.rag;

import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.module.qa.entity.QaDocument;
import com.ruc.college.module.qa.entity.QaDocumentChunk;
import com.ruc.college.module.qa.mapper.QaDocumentChunkMapper;
import com.ruc.college.module.qa.mapper.QaDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentRagService {

    private final QaDocumentMapper documentMapper;
    private final QaDocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    @Value("${rag.chunk-size:700}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:120}")
    private int chunkOverlap;

    @Value("${rag.top-k:4}")
    private int topK;

    @Value("${rag.min-score:0.3}")
    private double minScore;

    @Value("${rag.rerank-pool-size:20}")
    private int rerankPoolSize;

    @Value("${rag.enabled:true}")
    private boolean enabled;

    /**
     * 切片边界识别. 命中以下任一格式即作为新章节起点:
     *   - 第X章/节/条 (法规体, 如《学籍管理规定》)
     *   - （一）/（1）/(一)/(1) 中文或半角括号 + 中文/阿拉伯数字 (培养方案"（一）培养目标"这类)
     *   - 一、/1、/二、 中文/阿拉伯数字 + 顿号 (常用条目编号)
     */
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(?m)(?=^\\s*(?:" +
                    "第[一二三四五六七八九十百千万零〇0-9]+[章节条]" +
                    "|[（(][一二三四五六七八九十0-9]+[）)]" +
                    "|[一二三四五六七八九十0-9]+、" +
                    ")\\s*)"
    );
    /** 父级标题: 仅 第X章/节 是 "容器"标题, 会被挂到后续 chunk; 第X条 / （X）/ X、 是叶子级条目, 自己成 chunk */
    private static final Pattern CHAPTER_HEADING_PATTERN = Pattern.compile("^\\s*第[一二三四五六七八九十百千万零〇0-9]+[章节]\\s+.*", Pattern.DOTALL);

    public Map<String, Object> indexDocument(Long documentId) {
        if (!enabled) {
            throw new BusinessException("当前开发环境未启用向量数据库，请在 application-dev.yml 中设置 rag.enabled=true 后再执行向量入库");
        }

        QaDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new BusinessException("文档不存在");
        if (!StringUtils.hasText(doc.getFilePath())) throw new BusinessException("文档文件路径为空");

        File file = new File(System.getProperty("user.dir"), StringUtils.cleanPath(doc.getFilePath()));
        if (!file.exists() || !file.isFile()) throw new BusinessException("文档文件不存在");

        String text = extractText(file, doc);
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("暂未从该文档中解析到文本，图片类文件请先人工整理为文字或PDF文本版");
        }

        List<String> chunks = splitText(text);
        try {
            chunkMapper.deleteByDocumentId(documentId);
            for (int i = 0; i < chunks.size(); i++) {
                QaDocumentChunk chunk = new QaDocumentChunk();
                chunk.setDocumentId(documentId);
                chunk.setTitle(doc.getTitle());
                chunk.setCategory(doc.getCategory());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setKeywords(extractKeywords(chunks.get(i)));
                String embedding = embeddingService.toVectorLiteral(embeddingService.embed(buildEmbeddingText(doc, chunks.get(i))));
                chunkMapper.insertChunk(chunk, embedding);
            }
        } catch (Exception e) {
            log.warn("RAG document indexing failed, documentId={}", documentId, e);
            throw new BusinessException("向量数据库未初始化或不可用，请先执行 deploy/sql/rag_pgvector.sql 并确认数据库使用 pgvector 镜像");
        }
        return Map.of("documentId", documentId, "chunks", chunks.size());
    }

    public List<QaDocumentChunk> retrieve(String question, String category) {
        if (!enabled) {
            return List.of();
        }

        try {
            String retrievalQuery = expandRetrievalQuery(question);
            String embedding = embeddingService.toVectorLiteral(embeddingService.embedQuery(retrievalQuery));
            int poolSize = Math.max(topK, topK * Math.max(1, rerankPoolSize));
            String effectiveCategory = StringUtils.hasText(category)
                    ? category
                    : (isCalendarIntent(question) ? "校历安排" : "");
            return chunkMapper.searchSimilar(embedding, effectiveCategory, poolSize)
                    .stream()
                    // 第一道闸: 用原始余弦相似度过滤明显不相关的片段, 关键词加权不能"救活"语义不相关
                    .filter(c -> RagScoringUtil.nullToZero(c.getScore()) >= minScore)
                    // 第二道: 重排基础上叠加关键词/意图/受众加权
                    .peek(c -> c.setScore(boostScore(question, c)))
                    .sorted((a, b) -> Double.compare(RagScoringUtil.nullToZero(b.getScore()), RagScoringUtil.nullToZero(a.getScore())))
                    .limit(Math.max(1, topK))
                    .toList();
        } catch (Exception e) {
            log.warn("RAG retrieve skipped because vector store is unavailable: {}", e.getMessage());
            return List.of();
        }
    }

    private static String expandRetrievalQuery(String question) {
        if (!StringUtils.hasText(question)) {
            return question;
        }
        String q = question.trim();
        if (isCalendarIntent(q) && !q.contains("校历")) {
            return q + "\n校历安排 学期安排 节假日 放假 调休 寒假 暑假 开学 上课 考试周";
        }
        return q;
    }

    private static boolean isCalendarIntent(String question) {
        String q = question == null ? "" : question;
        String[] terms = {
                "校历", "节假日", "节日", "放假", "调休", "假期", "寒假", "暑假",
                "开学", "上课", "学期", "考试周", "报到",
                "国庆", "中秋", "元旦", "清明", "劳动节", "端午"
        };
        for (String term : terms) {
            if (q.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public String buildContext(String question) {
        List<QaDocumentChunk> chunks = retrieve(question, null);
        return buildContext(chunks);
    }

    public String buildContext(List<QaDocumentChunk> chunks) {
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            QaDocumentChunk c = chunks.get(i);
            if (i > 0) sb.append("\n\n");
            sb.append("资料").append(i + 1).append("：").append(RagScoringUtil.nullToEmpty(c.getTitle()));
            if (StringUtils.hasText(c.getCategory())) {
                sb.append("（").append(c.getCategory()).append("）");
            }
            sb.append("\n").append(c.getContent());
        }
        return sb.toString();
    }

    public int countChunks(Long documentId) {
        return chunkMapper.countByDocumentId(documentId);
    }

    private String extractText(File file, QaDocument doc) {
        String name = file.getName().toLowerCase();
        String type = doc.getFileType() == null ? "" : doc.getFileType().toLowerCase();
        if (name.endsWith(".pdf") || type.contains("pdf")) {
            return extractPdfText(file);
        }
        if (name.endsWith(".docx") || type.contains("word") || type.contains("docx")) {
            return extractDocxText(file);
        }
        if (name.endsWith(".txt") || type.contains("text/plain") || type.contains("txt")) {
            return extractTxtText(file);
        }
        return "";
    }

    private String extractPdfText(File file) {
        try (PDDocument pdf = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return normalizeText(stripper.getText(pdf));
        } catch (Exception e) {
            throw new BusinessException("PDF解析失败: " + e.getMessage());
        }
    }

    private String extractDocxText(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    String text = para.getText();
                    if (StringUtils.hasText(text)) {
                        sb.append(text.trim()).append("\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            if (StringUtils.hasText(cellText)) {
                                cells.add(cellText.trim());
                            }
                        }
                        if (!cells.isEmpty()) {
                            sb.append(String.join(" | ", cells)).append("\n");
                        }
                    }
                }
            }
            return normalizeText(sb.toString());
        } catch (Exception e) {
            throw new BusinessException("Word文档解析失败: " + e.getMessage());
        }
    }

    private String extractTxtText(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);
            return normalizeText(text);
        } catch (Exception e) {
            throw new BusinessException("文本文件读取失败: " + e.getMessage());
        }
    }

    private List<String> splitText(String text) {
        String normalized = normalizeText(text);
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(normalized)) return chunks;

        List<String> articleBlocks = splitByArticles(normalized);
        if (articleBlocks.size() > 1) {
            int maxArticleChunkSize = Math.max(300, chunkSize);
            for (String block : articleBlocks) {
                addChunkPreservingParagraphs(chunks, block, maxArticleChunkSize);
            }
            return chunks;
        }

        addChunkPreservingParagraphs(chunks, normalized, Math.max(300, chunkSize));
        return chunks;
    }

    private static List<String> splitByArticles(String text) {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = ARTICLE_PATTERN.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        if (starts.isEmpty()) {
            return List.of(text);
        }

        if (starts.get(0) > 0) {
            addIfText(blocks, text.substring(0, starts.get(0)));
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            addIfText(blocks, text.substring(start, end));
        }
        return attachHeadingsToFollowingArticle(blocks);
    }

    private static List<String> attachHeadingsToFollowingArticle(List<String> blocks) {
        List<String> result = new ArrayList<>();
        String pendingHeading = "";
        for (String block : blocks) {
            if (CHAPTER_HEADING_PATTERN.matcher(block).matches()) {
                pendingHeading = StringUtils.hasText(pendingHeading) ? pendingHeading + "\n" + block.trim() : block.trim();
                continue;
            }
            String content = StringUtils.hasText(pendingHeading) ? pendingHeading + "\n" + block.trim() : block.trim();
            pendingHeading = "";
            addIfText(result, content);
        }
        addIfText(result, pendingHeading);
        return result;
    }

    private static void addChunkPreservingParagraphs(List<String> chunks, String text, int maxSize) {
        String block = text == null ? "" : text.trim();
        if (!StringUtils.hasText(block)) {
            return;
        }
        if (block.length() <= maxSize) {
            chunks.add(block);
            return;
        }

        List<String> paragraphs = splitParagraphs(block);
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > maxSize) {
                addIfText(chunks, current.toString());
                current.setLength(0);
                addLengthChunks(chunks, paragraph, maxSize);
                continue;
            }
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > maxSize) {
                addIfText(chunks, current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(paragraph);
        }
        addIfText(chunks, current.toString());
    }

    private static List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        String[] lines = text.split("\\n+");
        StringBuilder current = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                addIfText(paragraphs, current.toString());
                current.setLength(0);
                continue;
            }
            if (startsListItem(line) && current.length() > 0) {
                addIfText(paragraphs, current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(line);
        }
        addIfText(paragraphs, current.toString());
        return paragraphs;
    }

    private static boolean startsListItem(String line) {
        return line.matches("^[(（][一二三四五六七八九十0-9]+[)）].*")
                || line.matches("^[0-9]+[.、].*");
    }

    private static void addLengthChunks(List<String> chunks, String text, int maxSize) {
        int overlap = Math.min(120, Math.max(0, maxSize / 8));
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxSize);
            int boundary = findSoftBoundary(text, start, end);
            if (boundary > start + maxSize / 2) {
                end = boundary;
            }
            addIfText(chunks, text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
    }

    private static int findSoftBoundary(String text, int start, int end) {
        int best = -1;
        String boundaries = "。\n；;！!？?";
        for (int i = end - 1; i > start; i--) {
            if (boundaries.indexOf(text.charAt(i)) >= 0) {
                best = i + 1;
                break;
            }
        }
        return best;
    }

    private static void addIfText(List<String> chunks, String text) {
        if (StringUtils.hasText(text)) {
            chunks.add(text.trim());
        }
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("(?m)^\\s*[-—–]\\s*\\d+\\s*[-—–]\\s*$", "")
                .replaceAll("(?m)^\\s*第\\s*\\d+\\s*页\\s*$", "")
                .replaceAll("(?m)^\\s*\\d+\\s*/\\s*\\d+\\s*$", "")
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" +", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static double boostScore(String question, QaDocumentChunk chunk) {
        double score = RagScoringUtil.nullToZero(chunk.getScore());
        Set<String> queryTerms = RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(question));
        String metadata = RagScoringUtil.nullToEmpty(chunk.getTitle()) + "\n"
                + RagScoringUtil.nullToEmpty(chunk.getCategory()) + "\n"
                + RagScoringUtil.nullToEmpty(chunk.getKeywords());
        String searchable = metadata + "\n" + RagScoringUtil.nullToEmpty(chunk.getContent());
        Set<String> documentTerms = RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(searchable));

        score += RagScoringUtil.weightedOverlap(queryTerms, documentTerms) * 0.55;
        score += RagScoringUtil.weightedOverlap(queryTerms, RagScoringUtil.expandTerms(RagScoringUtil.extractTerms(metadata))) * 0.25;
        score += RagScoringUtil.intentStructureScore(question, RagScoringUtil.nullToEmpty(chunk.getContent())) * 0.35;
        score += RagScoringUtil.audienceScopeScore(question, RagScoringUtil.nullToEmpty(chunk.getTitle())) * 0.45;
        return score;
    }

    private static String extractKeywords(String text) {
        if (!StringUtils.hasText(text)) return "";
        LinkedHashSet<String> words = new LinkedHashSet<>();
        Matcher matcher = RagScoringUtil.TOKEN_PATTERN.matcher(text);
        while (matcher.find() && words.size() < 20) {
            words.add(matcher.group());
        }
        return String.join(",", words);
    }

    private static String buildEmbeddingText(QaDocument doc, String content) {
        return RagScoringUtil.nullToEmpty(doc.getTitle()) + "\n"
                + RagScoringUtil.nullToEmpty(doc.getCategory()) + "\n" + content;
    }

}
