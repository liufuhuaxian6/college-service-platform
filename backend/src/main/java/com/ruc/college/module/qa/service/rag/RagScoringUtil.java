package com.ruc.college.module.qa.service.rag;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RagScoringUtil {

    private RagScoringUtil() {}

    static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9]{2,}");
    static final Pattern AUDIENCE_PATTERN = Pattern.compile(
            "本科生|本科|研究生|硕士生|博士生|新生|国际学生|学生|党员|团员");

    static final List<Set<String>> DOMAIN_SYNONYM_GROUPS = List.of(
            Set.of("延迟入学", "延期入学", "暂缓入学", "保留入学资格"),
            Set.of("报到", "报道"),
            Set.of("处分", "纪律处分", "违纪处理"),
            Set.of("休学", "暂停学业"),
            Set.of("退学", "终止学籍"),
            Set.of("毕业", "准予毕业"),
            Set.of("学习年限", "最长学习年限", "修业年限"),
            Set.of("类型", "种类", "类别", "等级"),
            Set.of("入党积极分子", "积极分子"),
            Set.of("预备党员", "预备期", "预备党员转正"),
            Set.of("转正", "按期转正", "预备党员转正"),
            Set.of("民主评议", "组织生活会", "民主生活会"),
            Set.of("入党申请书", "入党申请", "申请入党"),
            Set.of("入团申请书", "入团申请", "申请入团"),
            Set.of("发展对象", "确定发展对象"),
            Set.of("培养联系人", "培养考察", "培养人"),
            Set.of("政治审查", "政审"),
            Set.of("支部大会", "党支部大会", "支部会议"),
            Set.of("团课", "团课学习", "团校培训"),
            Set.of("党课", "党课学习", "党校培训"),
            Set.of("三会一课", "支部党员大会", "支部委员会", "党小组会"),
            Set.of("团员", "共青团员"),
            Set.of("党员", "中共党员", "正式党员"),
            Set.of("团支部", "团组织", "基层团组织"),
            Set.of("党支部", "党组织", "基层党组织")
    );

    public static Set<String> extractTerms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) return terms;
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            addTermAndNgrams(terms, matcher.group());
        }
        return terms;
    }

    public static Set<String> extractAudienceTerms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) return terms;
        Matcher matcher = AUDIENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            terms.add(normalizeAudienceTerm(matcher.group()));
        }
        return terms;
    }

    public static String normalizeAudienceTerm(String value) {
        return switch (value) {
            case "本科生" -> "本科";
            case "硕士生" -> "硕士";
            case "博士生" -> "博士";
            default -> value;
        };
    }

    public static Set<String> expandTerms(Set<String> terms) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>(terms);
        for (Set<String> group : DOMAIN_SYNONYM_GROUPS) {
            boolean matched = group.stream().anyMatch(expanded::contains);
            if (matched) {
                for (String synonym : group) {
                    addTermAndNgrams(expanded, synonym);
                }
            }
        }
        return expanded;
    }

    public static void addTermAndNgrams(Set<String> terms, String term) {
        if (!StringUtils.hasText(term)) return;
        String value = term.trim();
        terms.add(value);
        if (value.chars().allMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN)) {
            int max = Math.min(4, value.length());
            for (int n = 2; n <= max; n++) {
                for (int i = 0; i + n <= value.length(); i++) {
                    terms.add(value.substring(i, i + n));
                }
            }
        }
    }

    public static double weightedOverlap(Set<String> queryTerms, Set<String> documentTerms) {
        if (queryTerms.isEmpty() || documentTerms.isEmpty()) return 0;
        double total = 0;
        double matched = 0;
        for (String term : queryTerms) {
            double weight = term.length() >= 4 ? 2.0 : (term.length() == 3 ? 1.5 : 1.0);
            total += weight;
            if (documentTerms.contains(term)) {
                matched += weight;
            }
        }
        return total == 0 ? 0 : matched / total;
    }

    public static double audienceScopeScore(String question, String title) {
        Set<String> queryAudience = extractAudienceTerms(question);
        if (queryAudience.isEmpty()) return 0;
        Set<String> titleAudience = extractAudienceTerms(title);
        if (titleAudience.isEmpty()) return 0;
        for (String audience : queryAudience) {
            if (titleAudience.contains(audience)) return 1.0;
        }
        return titleAudience.stream().anyMatch(a -> !a.equals("学生")) ? -0.45 : 0;
    }

    public static double intentStructureScore(String question, String content) {
        String q = nullToEmpty(question);
        String c = nullToEmpty(content);
        double score = 0;
        if ((q.contains("什么情况") || q.contains("哪些情况") || q.contains("条件") || q.contains("可以"))
                && (c.contains("下列情况") || c.contains("条件") || c.contains("可以申请"))) {
            score += 1.0;
        }
        if ((q.contains("如何") || q.contains("怎么") || q.contains("流程") || q.contains("步骤"))
                && (c.contains("程序") || c.contains("流程") || c.contains("步骤") || c.contains("申请"))) {
            score += 1.0;
        }
        if ((q.contains("多久") || q.contains("多少") || q.contains("期限") || q.contains("年限"))
                && (c.contains("年") || c.contains("月") || c.contains("期限") || c.contains("年限"))) {
            score += 1.0;
        }
        if (asksForTypeList(q) && (c.contains("种类") || c.contains("类型") || c.contains("类别") || c.contains("分为"))) {
            score += 1.0;
        }
        if (asksForTypeList(q) && hasQueryTermNearTypeHeading(q, c)) {
            score += 2.0;
        }
        if (asksForTime(q) && hasQueryTermNearTimeExpression(q, c)) {
            score += 2.0;
        }
        return Math.min(score, 3.0);
    }

    public static boolean asksForTypeList(String question) {
        return question.contains("类型") || question.contains("种类")
                || question.contains("类别") || question.contains("等级")
                || question.contains("有哪些") || question.contains("包括哪些");
    }

    public static boolean asksForTime(String question) {
        return question.contains("什么时候") || question.contains("何时")
                || question.contains("时间") || question.contains("日期")
                || question.contains("哪天") || question.contains("几号");
    }

    public static boolean hasQueryTermNearTypeHeading(String question, String content) {
        Set<String> queryTerms = extractTerms(question);
        for (String term : queryTerms) {
            if (term.length() >= 2
                    && (content.contains(term + "种类") || content.contains(term + "类型") || content.contains(term + "类别"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasQueryTermNearTimeExpression(String question, String content) {
        Set<String> queryTerms = extractTerms(question);
        String[] timeTerms = {"时间", "日期", "报到日", "报到时", "开学前", "开学后", "期限", "日", "天", "月", "年"};
        for (String queryTerm : queryTerms) {
            if (queryTerm.length() < 2) continue;
            int position = content.indexOf(queryTerm);
            while (position >= 0) {
                int from = Math.max(0, position - 80);
                int to = Math.min(content.length(), position + 120);
                String window = content.substring(from, to);
                for (String timeTerm : timeTerms) {
                    if (window.contains(timeTerm)) return true;
                }
                position = content.indexOf(queryTerm, position + queryTerm.length());
            }
        }
        return false;
    }

    public static double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
