package com.weatherfit.weatherfitBackend.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnonymousFilteringConsumer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "weatherfit.anonymous", groupId = "weatherfit-anonymous-filter")
    public void consume(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);

            if (isValid(data)) {
                kafkaTemplate.send("weatherfit-anonymous-success", message);
                log.info("[Anonymous] 필터링 성공: {}", message);
            } else {
                kafkaTemplate.send("weatherfit-anonymous-fail", message);
                log.warn("[Anonymous] 필터링 실패 (필수값 누락): {}", message);
            }

        } catch (Exception e) {
            kafkaTemplate.send("weatherfit-anonymous-fail", message);
            log.error("[Anonymous] 파싱 오류: {}", e.getMessage());
        }
    }

    private boolean isValid(Map<String, Object> data) {
        // anonymous_id, visited_at 필수
        if (!data.containsKey("anonymous_id") || data.get("anonymous_id") == null) return false;
        if (!data.containsKey("visited_at") || data.get("visited_at") == null) return false;

        // UUID 형식 검증 (36자리)
        String anonymousId = String.valueOf(data.get("anonymous_id"));
        if (anonymousId.length() != 36) return false;

        return true;
    }
}