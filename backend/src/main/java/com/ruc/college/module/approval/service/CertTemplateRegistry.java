package com.ruc.college.module.approval.service;

import com.ruc.college.module.auth.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 证明模板注册表。
 *
 * <p>模板文件只作为离线参考，运行时不再解析 docx。提交申请后，系统根据学生档案
 * 与表单补充字段，按这里固化的版式生成证明正文。</p>
 */
public final class CertTemplateRegistry {

    private CertTemplateRegistry() {
    }

    @Data
    @AllArgsConstructor
    public static class FieldSpec {
        private String key;
        private String label;
        private String type;
        private List<String> options;
        private String placeholder;
        private boolean required;
    }

    public interface CertTemplate {
        List<FieldSpec> getStudentInputs();

        List<String> buildLines(SysUser user, Map<String, Object> form, String appNo);
    }

    private static final Map<String, CertTemplate> REGISTRY = Map.of(
            "党员证明模板", new PartyMemberCert(),
            "团员证明模板", new LeagueMemberCert()
    );

    public static CertTemplate byTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return GENERIC;
        }
        CertTemplate direct = REGISTRY.get(title.trim());
        if (direct != null) {
            return direct;
        }
        if (title.contains("党员")) {
            return REGISTRY.get("党员证明模板");
        }
        if (title.contains("团员")) {
            return REGISTRY.get("团员证明模板");
        }
        return GENERIC;
    }

    public static Map<String, String> profileValues(SysUser user) {
        Map<String, String> values = new LinkedHashMap<>();
        if (user == null) {
            return values;
        }
        putIfPresent(values, "姓名", user.getName());
        putIfPresent(values, "学号", user.getStudentId());
        putIfPresent(values, "年级", user.getGrade());
        putIfPresent(values, "专业", user.getMajor());
        putIfPresent(values, "班级", user.getClassName());
        putIfPresent(values, "手机号", user.getPhone());
        return values;
    }

    public static class PartyMemberCert implements CertTemplate {
        @Override
        public List<FieldSpec> getStudentInputs() {
            return List.of(
                    new FieldSpec("身份证号", "身份证号", "text", null, "请输入身份证号", true),
                    new FieldSpec("学历", "培养层次", "select",
                            List.of("本科生", "硕士生", "博士生"),
                            "请选择当前培养层次", true),
                    new FieldSpec("入党日期", "入党日期", "date", null,
                            "请选择实际入党日期", true),
                    new FieldSpec("党支部", "所属党支部", "text", null,
                            "如：学生第一党支部", true),
                    new FieldSpec("用途", "证明用途", "text", null,
                            "如：政审、组织关系转接、材料归档", true)
            );
        }

        @Override
        public List<String> buildLines(SysUser user, Map<String, Object> form, String appNo) {
            String name = userValue(user, SysUser::getName);
            String studentId = userValue(user, SysUser::getStudentId);
            String major = userValue(user, SysUser::getMajor);
            String grade = userValue(user, SysUser::getGrade);
            String idCard = value(form, "身份证号");
            String degree = value(form, "学历");
            String partyDate = formatDate(value(form, "入党日期"));
            String branch = value(form, "党支部");

            List<String> lines = new ArrayList<>();
            lines.add("证明");
            lines.add("");
            lines.add("兹证明中国人民大学信息学院" + name + "，学号：" + studentId + "，身份证号：" + idCard + "，");
            lines.add("为我院" + major + "专业" + grade + "级" + degree + "，该生于" + partyDate + "入党，");
            lines.add("目前组织关系属于中国人民大学信息学院党委" + branch + "。");
            lines.add("");
            lines.add("联系人：胡昊");
            lines.add("联系电话：010-62513007");
            lines.add("");
            lines.add("中国人民大学信息学院党委");
            lines.add(todayChinese());
            return lines;
        }
    }

    public static class LeagueMemberCert implements CertTemplate {
        @Override
        public List<FieldSpec> getStudentInputs() {
            return List.of(
                    new FieldSpec("身份证号", "身份证号", "text", null, "请输入身份证号", true),
                    new FieldSpec("学历", "培养层次", "select",
                            List.of("本科生", "硕士生", "博士生"),
                            "请选择当前培养层次", true),
                    new FieldSpec("入团日期", "入团日期", "date", null,
                            "请选择实际入团日期", true),
                    new FieldSpec("团员编号", "团员编号", "text", null,
                            "请输入团员编号", true),
                    new FieldSpec("用途", "证明用途", "text", null,
                            "如：入党申请、组织关系转接", true)
            );
        }

        @Override
        public List<String> buildLines(SysUser user, Map<String, Object> form, String appNo) {
            String name = userValue(user, SysUser::getName);
            String studentId = userValue(user, SysUser::getStudentId);
            String className = userValue(user, SysUser::getClassName);
            String idCard = value(form, "身份证号");
            String degree = value(form, "学历");
            String leagueDate = formatDate(value(form, "入团日期"));
            String memberNo = value(form, "团员编号");

            List<String> lines = new ArrayList<>();
            lines.add("证明");
            lines.add("");
            lines.add("兹证明中国人民大学信息学院" + name + "，学号：" + studentId + "，身份证号：" + idCard + "，");
            lines.add("为我院" + className + "班" + degree + "，该生于" + leagueDate + "加入中国共产主义青年团，");
            lines.add("团员编号：" + memberNo + "。");
            lines.add("特此证明。");
            lines.add("");
            lines.add("联系人：胡昊");
            lines.add("联系电话：010-62513007");
            lines.add("");
            lines.add("中国人民大学信息学院团委");
            lines.add(todayChinese());
            return lines;
        }
    }

    private static final CertTemplate GENERIC = new CertTemplate() {
        @Override
        public List<FieldSpec> getStudentInputs() {
            return List.of(
                    new FieldSpec("用途", "证明用途", "text", null, "请填写本次证明用途", true),
                    new FieldSpec("份数", "份数", "number", null, "1", false),
                    new FieldSpec("备注", "备注", "text", null, "可选", false)
            );
        }

        @Override
        public List<String> buildLines(SysUser user, Map<String, Object> form, String appNo) {
            List<String> lines = new ArrayList<>();
            lines.add("学院学生综合服务平台 - 电子证明");
            lines.add("申请编号：" + safe(appNo));
            lines.add("姓名：" + userValue(user, SysUser::getName));
            lines.add("学号：" + userValue(user, SysUser::getStudentId));
            lines.add("年级 / 专业：" + userValue(user, SysUser::getGrade) + " / " + userValue(user, SysUser::getMajor));
            lines.add("班级：" + userValue(user, SysUser::getClassName));
            lines.add("用途：" + value(form, "用途"));
            lines.add("份数：" + value(form, "份数"));
            lines.add("备注：" + value(form, "备注"));
            lines.add("生成日期：" + todayChinese());
            return lines;
        }
    };

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }

    private static String value(Map<String, Object> form, String key) {
        Object value = form == null ? null : form.get(key);
        return safe(value == null ? "" : String.valueOf(value));
    }

    private static String userValue(SysUser user, java.util.function.Function<SysUser, String> getter) {
        return user == null ? "____" : safe(getter.apply(user));
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "____";
    }

    private static String formatDate(String raw) {
        if (!StringUtils.hasText(raw) || "____".equals(raw)) {
            return "____年__月__日";
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim());
            return date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String todayChinese() {
        LocalDate today = LocalDate.now();
        return today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
    }
}
