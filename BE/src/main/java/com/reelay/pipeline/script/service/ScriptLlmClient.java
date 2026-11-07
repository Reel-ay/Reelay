package com.reelay.pipeline.script.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.exception.OpenAiErrors;
import com.reelay.common.exception.OpenAiTransientException;
import com.reelay.pipeline.script.dto.LlmScript;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

// LLM 1회 호출. 재시도 단위를 "호출 1번"으로 두려고 ScriptService 와 분리했다
// (@Retryable 은 프록시로 동작해서 같은 클래스 안에서 부르면 재시도가 걸리지 않는다)
@Component
public class ScriptLlmClient {

    private final ChatClient chatClient;

    public ScriptLlmClient(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // 429·5xx·네트워크 오류만 3회 재시도 (1s / 2s / 4s). 그 외는 즉시 실패
    @Retryable(includes = OpenAiTransientException.class, maxRetries = 3, delay = 1000, multiplier = 2)
    public LlmScript call(String systemPrompt, String userPrompt) {
        LlmScript result;
        try {
            result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(LlmScript.class);
        }
        catch (RuntimeException e) {
            throw OpenAiErrors.translate(e, ErrorCode.SCRIPT_FAILED);
        }
        if (result == null) {
            throw new BusinessException(ErrorCode.SCRIPT_FAILED);
        }
        return result;
    }
}
