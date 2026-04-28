package com.ruc.college.module.qa.service.ai;

/**
 * AI 大模型统一接口 (Strategy 模式)
 * 后续可实现不同的大模型提供者
 */
public interface AiProvider {

    /**
     * 对话问答
     * @param question 用户问题
     * @param context  知识库上下文（检索到的相关内容）
     * @return AI 生成的回答
     */
    String chat(String question, String context);

    /**
     * 提供者名称
     */
    String getName();

    /**
     * 是否可用
     */
    boolean isAvailable();
}
