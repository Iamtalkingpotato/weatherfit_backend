package com.weatherfit.weatherfitBackend.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FilteringConsumer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "weatherfit-log-raw", groupId = "weatherfit-consumer")
    public void consume(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);

            if (data.containsKey("event_type")) {
                String raw = String.valueOf(data.get("event_type")).toUpperCase();
                if (raw.startsWith("WISHLIST")) raw = "WISHLIST";
                data.put("event_type", raw);
            }

            if (isValid(data)) {
                kafkaTemplate.send("weatherfit-log-success", objectMapper.writeValueAsString(data));
                log.info("필터링 성공: {}", message);
            } else {
                String reason = getFailReason(data);
                sendFail(data, reason);
                log.warn("필터링 실패 [{}]: {}", reason, message);
            }

        } catch (Exception e) {
            try {
                Map<String, Object> failData = new HashMap<>();
                failData.put("raw", message);
                failData.put("_fail_reason", "JSON 파싱 오류: " + e.getMessage());
                kafkaTemplate.send("weatherfit-log-fail", objectMapper.writeValueAsString(failData));
            } catch (Exception ex) {
                kafkaTemplate.send("weatherfit-log-fail", message);
            }
            log.error("파싱 오류: {}", e.getMessage());
        }
    }

    private void sendFail(Map<String, Object> data, String reason) {
        try {
            Map<String, Object> failData = new HashMap<>(data);
            failData.put("_fail_reason", reason);
            kafkaTemplate.send("weatherfit-log-fail", objectMapper.writeValueAsString(failData));
        } catch (Exception e) {
            log.error("실패 토픽 전송 오류: {}", e.getMessage());
        }
    }

    private String getFailReason(Map<String, Object> data) {
        String eventType = normalizeEventType(data);

        if (eventType == null) {
            return "event_type / action 필드 없음";
        }

        if (!data.containsKey("customer_id")) {
            return "customer_id 필드 없음";
        }

        if (String.valueOf(data.get("customer_id")).startsWith("anon_")) {
            return "비로그인 사용자(anon_) — customer_id 없음";
        }

        return switch (eventType) {
    case "SCROLL", "scroll"                   -> "알 수 없는 오류";
    case "search", "SEARCH"                   -> "알 수 없는 오류";
    case "login", "LOGIN"                     -> "알 수 없는 오류";
    case "SIGNUP", "signup"                   -> "email 필드 없음";
    case "CUSTOMER_UPDATE", "customer_update" -> "알 수 없는 오류";
    case "WISHLIST"                                  -> "product_id 필드 없음";
    case "add_to_cart"                               -> "product_id, price, size 중 필드 없음";
    case "purchase"                                  -> "product_id, price, size 중 필드 없음";
    case "wardrobe_add"                              -> "category 또는 style 필드 없음";
    case "feedback" -> {
        Object fb = data.get("feedback");
        if (fb == null) yield "feedback 값 없음";
        String value = String.valueOf(fb);
        if (!value.equals("HOT") && !value.equals("COLD") && !value.equals("PERFECT"))
            yield "유효하지 않은 feedback 값: " + value;
        yield "actual_temp 필드 없음";
    }
    default                -> "지원하지 않는 event_type: " + eventType;
};
    }

    private boolean isValid(Map<String, Object> data) {

    String eventType = normalizeEventType(data); 
    if (eventType == null) return false; 
    if (!data.containsKey("customer_id")) return false;
    if (String.valueOf(data.get("customer_id")).startsWith("anon_")) return false;  

    return switch (eventType) {
        case "page_view", "product_view", "outfit_view" -> true;
        case "SCROLL", "scroll"                          -> true;
        case "search", "SEARCH"                          -> true;
        case "login", "LOGIN"                            -> true;
        case "SIGNUP", "signup"                          -> data.containsKey("email");
        case "CUSTOMER_UPDATE", "customer_update"        -> true;
        case "WISHLIST"           -> data.containsKey("product_id");
        case "add_to_cart"        -> data.containsKey("product_id") && data.containsKey("price");
        case "purchase"           -> data.containsKey("product_id") && data.containsKey("price");
        case "wardrobe_add"       -> data.containsKey("category") && data.containsKey("style");
        case "feedback"           -> {
            Object fb = data.get("feedback");
            if (fb == null) yield false;
            String val = String.valueOf(fb);
            yield (val.equals("HOT") || val.equals("COLD") || val.equals("PERFECT"))
                  && data.containsKey("actual_temp");
        }
        default -> false;
    };
}

    private boolean hasProductId(Map<String, Object> data) {
        return data.containsKey("product_id") || data.containsKey("item_id");
    }

    private String normalizeEventType(Map<String, Object> data) {
        Object raw = data.get("event_type");
        if (raw == null) raw = data.get("action");
        if (raw == null || String.valueOf(raw).equals("null")) return null;

        return switch (String.valueOf(raw)) {
            case "PAGE_VIEW"       -> "page_view";
            case "PRODUCT_VIEW"    -> "product_view";
            case "OUTFIT_VIEW"     -> "outfit_view";
            case "SCROLL"          -> "scroll";
            case "SEARCH"          -> "search";
            case "LOGIN"           -> "login";
            case "SIGNUP"          -> "signup";
            case "CUSTOMER_UPDATE" -> "customer_update";
            case "ADD_TO_CART"     -> "add_to_cart";
            case "WISHLIST_ADD"    -> "wishlist_add";
            case "WISHLIST_REMOVE" -> "wishlist_remove";
            case "WARDROBE_ADD"    -> "wardrobe_add";
            case "PURCHASE"        -> "purchase";
            case "FEEDBACK"        -> "feedback";
            default                -> String.valueOf(raw);
        };
    }
}