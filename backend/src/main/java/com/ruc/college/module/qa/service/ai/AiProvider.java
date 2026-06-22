package com.ruc.college.module.qa.service.ai;

/**
 * AI 大模型统一接口 (Strategy 模式)
 * 后续可实现不同的大模型提供者
 */
public interface AiProvider {

    /**
     * 对话问答（单轮）
     * @param question 用户问题
     * @param context  知识库上下文（检索到的相关内容）
     * @return AI 生成的回答
     */
    String chat(String question, String context);

    /**
     * 对话问答（多轮）。默认忽略历史、退化为单轮，支持上下文的实现可重写。
     * @param history 之前的对话, 每项 {role: user|assistant, content}, 不含当前问题
     */
    default String chat(String question, String context,
                        java.util.List<java.util.Map<String, String>> history) {
        return chat(question, context);
    }

    /**
     * 提供者名称
     */
    String getName();

    /**
     * 是否可用
     */
    boolean isAvailable();
}
