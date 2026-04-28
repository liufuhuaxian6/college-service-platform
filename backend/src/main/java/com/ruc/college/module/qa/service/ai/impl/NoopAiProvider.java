package com.ruc.college.module.qa.service.ai.impl;

import com.ruc.college.module.qa.service.ai.AiProvider;
import org.springframework.stereotype.Component;

/**
 * 空实现：当未配置任何 AI 模型时使用
 * 直接返回人工兜底提示
 */
@Component
public class NoopAiProvider implements AiProvider {

    @Override
    public String chat(String question, String context) {
        return "暂未找到匹配的标准答案，建议您联系辅导员获取准确信息。";
    }

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
