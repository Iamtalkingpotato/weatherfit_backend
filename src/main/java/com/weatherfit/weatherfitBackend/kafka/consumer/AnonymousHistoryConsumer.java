package com.weatherfit.weatherfitBackend.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherfit.weatherfitBackend.domain.entity.AnonymousUser;
import com.weatherfit.weatherfitBackend.domain.entity.AnonymousVisit;
import com.weatherfit.weatherfitBackend.domain.entity.AnonymousVisitFailure;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousUserRepository;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousVisitRepository;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousVisitFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnonymousHistoryConsumer {

    private final AnonymousUserRepository anonymousUserRepository;
    private final AnonymousVisitRepository anonymousVisitRepository;
    private final AnonymousVisitFailureRepository anonymousVisitFailureRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 성공 토픽: anonymous_visits 저장 + anonymous_users upsert
    @KafkaListener(topics = "weatherfit-anonymous-success", groupId = "weatherfit-anonymous-history")
    public void consumeSuccess(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String anonymousId = String.valueOf(data.get("anonymous_id"));
            String ip          = data.containsKey("ip") ? String.valueOf(data.get("ip")) : null;
            String pageUrl     = data.containsKey("page_url") ? String.valueOf(data.get("page_url")) : null;
            String userAgent   = data.containsKey("user_agent") ? String.valueOf(data.get("user_agent")) : null;
            LocalDateTime now  = LocalDateTime.now();

            // 1. anonymous_visits 저장
            AnonymousVisit visit = AnonymousVisit.builder()
                    .anonymousId(anonymousId)
                    .ip(ip)
                    .pageUrl(pageUrl)
                    .userAgent(userAgent)
                    .visitedAt(now)
                    .topic("weatherfit-anonymous-success")
                    .build();
            anonymousVisitRepository.save(visit);

            // 2. anonymous_users upsert (있으면 방문수+1, 없으면 신규 생성)
            Optional<AnonymousUser> existing = anonymousUserRepository.findByAnonymousId(anonymousId);
            if (existing.isPresent()) {
                AnonymousUser user = existing.get();
                user.setVisitCount(user.getVisitCount() + 1);
                user.setLastVisit(now);
                if (ip != null) user.setIp(ip);
                anonymousUserRepository.save(user);
            } else {
                AnonymousUser newUser = AnonymousUser.builder()
                        .anonymousId(anonymousId)
                        .ip(ip)
                        .visitCount(1)
                        .firstVisit(now)
                        .lastVisit(now)
                        .build();
                anonymousUserRepository.save(newUser);
            }

            log.info("[Anonymous] ✅ 저장 완료: {}", anonymousId);

        } catch (Exception e) {
            log.error("[Anonymous] ❌ 성공 토픽 처리 오류: {}", e.getMessage());
        }
    }

    // 실패 토픽: anonymous_visit_failures 저장
    @KafkaListener(topics = "weatherfit-anonymous-fail", groupId = "weatherfit-anonymous-history")
    public void consumeFail(String message) {
        try {
            AnonymousVisitFailure failure = AnonymousVisitFailure.builder()
                    .rawData(message)
                    .failReason("필수 필드 누락 또는 파싱 오류")
                    .topic("weatherfit-anonymous-fail")
                    .build();
            anonymousVisitFailureRepository.save(failure);
            log.warn("[Anonymous] ⚠️ 실패 이력 저장: {}", message);
        } catch (Exception e) {
            log.error("[Anonymous] ❌ 실패 토픽 처리 오류: {}", e.getMessage());
        }
    }
}