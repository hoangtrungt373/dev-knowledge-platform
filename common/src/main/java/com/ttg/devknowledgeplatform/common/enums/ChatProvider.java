package com.ttg.devknowledgeplatform.common.enums;

/**
 * Vendor backing a selectable chat model — determines which LangChain4j builder family
 * {@code AiServiceConfig} uses to construct that model's bean.
 */
public enum ChatProvider {
    OPENAI,
    ANTHROPIC
}
