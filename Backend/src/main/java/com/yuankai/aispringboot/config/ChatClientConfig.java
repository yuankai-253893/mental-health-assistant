package com.yuankai.aispringboot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(30)        // 保留最新30条消息
                .build();
    }

    // 聊天室配置
    @Bean("open-ai")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                .defaultSystem("你是一个专业的心理疏导师，温和耐心，善于倾听，能够提供专业的心理支持和建议").build();
    }

    /**
     * 情绪分析专用 ChatClient：刻意不挂会话记忆顾问。
     * 分析任务针对某一条独立日记，与咨询对话的上下文无关；
     * 若复用带记忆的 bean，会把无关的对话历史带进分析请求，污染结果并白白消耗 token。
     * 系统提示词在调用处按分析场景单独传入。
     */
    @Bean("analysis-ai")
    public ChatClient analysisChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }
}
