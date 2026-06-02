package com.weatherfit.weatherfitBackend.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherfit.weatherfitBackend.domain.entity.*;
import com.weatherfit.weatherfitBackend.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryConsumer {

    private static final String SUCCESS_GROUP = "weatherfit-history-consumer";

    private final ProcessSuccessLogRepository successLogRepository;
    private final ProcessFailLogRepository failLogRepository;
    private final CustomerRepository customerRepository;
    private final TemperatureFeedbackRepository feedbackRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final BehaviorLogRepository behaviorLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "weatherfit-log-success", groupId = SUCCESS_GROUP)
    public void consumeSuccess(ConsumerRecord<String, String> record) {
        String message = record.value();
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String eventType = normalizeEventType(data);
            if (eventType == null) return;

            Long customerId = parseCustomerId(data);

            successLogRepository.save(ProcessSuccessLog.builder()
                    .customerId(customerId)
                    .topic(record.topic())
                    .partitionNo(record.partition())
                    .offsetValue(record.offset())
                    .dataType(eventType)
                    .consumerGroup(SUCCESS_GROUP)
                    .rawMessage(message)
                    .processedAt(LocalDateTime.now())
                    .build());

            switch (eventType) {
                case "SIGNUP"                       -> saveCustomer(data);
                case "FEEDBACK"                     -> saveFeedback(data);
                case "PURCHASE", "WISHLIST", "CART" -> savePurchase(data);
                case "WARDROBE"                     -> saveWardrobe(data);
                case "VIEW", "SCROLL", "LOGIN"      -> saveBehaviorLog(data, eventType);
                case "add_to_cart", "wishlist_add", "purchase" -> {
                    savePurchase(data);
                    saveBehaviorLog(data, eventType);
                }
                case "wishlist_remove" -> {
                    removeWishlist(data);
                    saveBehaviorLog(data, eventType);
                }
                case "page_view", "product_view", "outfit_view", "search",
                     "wardrobe_add", "feedback" -> saveBehaviorLog(data, eventType);
            }
            updateLastLoginDate(data);

        } catch (Exception e) {
            log.error("성공 이력 저장 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "weatherfit-log-fail", groupId = "weatherfit-history-consumer")
public void consumeFail(String message) {
    try {
        // 기존: failReason 하드코딩
        // 수정: 메시지에서 _fail_reason 추출
        String failReason = "필터링 실패";
        try {
            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            Object reason = parsed.get("_fail_reason");
            if (reason != null) failReason = String.valueOf(reason);
        } catch (Exception ignored) {}

        ProcessFailLog failLog = ProcessFailLog.builder()
                .topic("weatherfit-log-fail")
                .rawMessage(message)
                .failReason(failReason)  // 동적으로 변경
                .processedAt(LocalDateTime.now())
                .build();
        failLogRepository.save(failLog);
    } catch (Exception e) {
        log.error("실패 이력 저장 실패: {}", e.getMessage());
    }
}

    private Long parseCustomerId(Map<String, Object> data) {
        Object raw = data.get("customer_id");
        if (raw == null || String.valueOf(raw).startsWith("uid_")) raw = data.get("partnerCustomerId");
        if (raw == null || String.valueOf(raw).equals("null")) return null;
        try { return Long.parseLong(String.valueOf(raw)); } catch (NumberFormatException e) { return null; }
    }

    private String normalizeEventType(Map<String, Object> data) {
        Object raw = data.get("event_type");
        if (raw == null) raw = data.get("action");
        if (raw == null || String.valueOf(raw).equals("null")) return null;

        return switch (String.valueOf(raw)) {
            case "PAGE_VIEW" -> "page_view";
            case "PRODUCT_VIEW" -> "product_view";
            case "OUTFIT_VIEW" -> "outfit_view";
            case "SEARCH" -> "search";
            case "ADD_TO_CART" -> "add_to_cart";
            case "WISHLIST_ADD" -> "wishlist_add";
            case "WISHLIST_REMOVE" -> "wishlist_remove";
            case "WARDROBE_ADD" -> "wardrobe_add";
            case "PURCHASE" -> "purchase";
            case "FEEDBACK" -> "feedback";
            default -> String.valueOf(raw);
        };
    }

    private void saveCustomer(Map<String, Object> data) {
        try {
            String email = String.valueOf(data.get("email"));
            if (customerRepository.existsByEmail(email)) return;
            customerRepository.save(Customer.builder()
                    .name(String.valueOf(data.get("name")))
                    .email(email)
                    .passwordHash(data.get("password_hash") != null ? String.valueOf(data.get("password_hash")) : null)
                    .phone(String.valueOf(data.get("phone")))
                    .gender(String.valueOf(data.get("gender")))
                    .birthDate(LocalDate.parse(String.valueOf(data.get("birth_date"))))
                    .joinDate(LocalDate.now())
                    .joinChannel(String.valueOf(data.getOrDefault("join_channel", "APP")))
                    .joinType(String.valueOf(data.getOrDefault("join_type", "DIRECT")))
                    .marketingConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("marketing_consent", false))))
                    .pushConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("push_consent", false))))
                    .emailConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("email_consent", false))))
                    .smsConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("sms_consent", false))))
                    .dmConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("dm_consent", false))))
                    .tmConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("tm_consent", false))))
                    .build());
            log.info("고객 저장 완료: {}", email);
        } catch (Exception e) {
            log.error("고객 저장 실패: {}", e.getMessage());
        }
    }

    private void saveFeedback(Map<String, Object> data) {
        try {
            Long customerId = parseCustomerId(data);
            if (customerId == null) { log.warn("피드백 저장 실패: customer_id 없음"); return; }
            feedbackRepository.save(TemperatureFeedback.builder()
                    .customerId(customerId)
                    .feedback(String.valueOf(data.get("feedback")))
                    .temperature(data.get("actual_temp") != null ? Double.parseDouble(String.valueOf(data.get("actual_temp"))) : null)
                    .feelsLikeTemp(data.get("feels_like_temp") != null ? Double.parseDouble(String.valueOf(data.get("feels_like_temp"))) : null)
                    .humidity(data.get("humidity") != null ? Integer.parseInt(String.valueOf(data.get("humidity"))) : null)
                    .windSpeed(data.get("wind_speed") != null ? Double.parseDouble(String.valueOf(data.get("wind_speed"))) : null)
                    .weatherCondition(String.valueOf(data.getOrDefault("weather_condition", "")))
                    .feedbackDate(data.get("feedback_date") != null ? LocalDate.parse(String.valueOf(data.get("feedback_date"))) : LocalDate.now())
                    .build());
            log.info("피드백 저장 완료");
        } catch (Exception e) {
            log.error("피드백 저장 실패: {}", e.getMessage());
        }
    }

    private void savePurchase(Map<String, Object> data) {
    try {
        Object customerId = data.get("customer_id");
        if (customerId == null || String.valueOf(customerId).equals("null")) {
            log.warn("구매 저장 실패: customer_id 없음");
            return;
        }
        String eventType = String.valueOf(data.get("event_type"));
        String status = switch (eventType) {
            case "WISHLIST" -> "WISHLIST";
            case "CART"     -> "CART";
            default         -> "PURCHASED";
        };

        Long cid = Long.parseLong(String.valueOf(customerId));
        Long pid = Long.parseLong(String.valueOf(data.get("product_id")));

        // 중복 체크
        if (purchaseRepository.existsByCustomerIdAndProductIdAndStatus(cid, pid, status)) {
            log.info("중복 데이터 skip: customerId={}, productId={}, status={}", cid, pid, status);
            return;
        }

        purchaseRepository.save(Purchase.builder()
                .customerId(cid)
                .productId(pid)
                .price(Integer.parseInt(String.valueOf(data.get("price"))))
                .status(status)
                .size(String.valueOf(data.getOrDefault("size", "")))
                .purchasedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());
        log.info("구매/찜/장바구니 저장 완료: {}", status);
    } catch (Exception e) {
        log.error("구매 저장 실패: {}", e.getMessage());
    }
}

    private void removeWishlist(Map<String, Object> data) {
        try {
            Long customerId = parseCustomerId(data);
            if (customerId == null) { log.warn("wishlist remove skipped: customer_id missing"); return; }
            Long productId = parseProductId(data);
            if (productId == null) { log.warn("wishlist remove skipped: product_id/item_id missing"); return; }

            purchaseRepository.deleteByCustomerIdAndProductIdAndStatus(customerId, productId, "WISHLIST");
            log.info("wishlist removed: customerId={}, productId={}", customerId, productId);
        } catch (Exception e) {
            log.error("wishlist remove failed: {}", e.getMessage());
        }
    }

    private void checkoutCartItems(Long customerId) {
        var cartItems = purchaseRepository.findByCustomerIdAndStatus(customerId, "CART");
        if (cartItems.isEmpty()) {
            log.warn("purchase checkout skipped: cart is empty for customerId={}", customerId);
            return;
        }

        cartItems.forEach(item -> {
            item.setStatus("PURCHASED");
            item.setPurchasedAt(LocalDateTime.now());
        });
        purchaseRepository.saveAll(cartItems);
        log.info("cart checkout completed: customerId={}, count={}", customerId, cartItems.size());
    }

    private Long parseProductId(Map<String, Object> data) {
        Object raw = data.get("product_id");
        if (raw == null || String.valueOf(raw).equals("null")) {
            raw = data.get("item_id");
        }
        if (raw == null || String.valueOf(raw).equals("null")) return null;
        try { return Long.parseLong(String.valueOf(raw)); } catch (NumberFormatException e) { return null; }
    }

    private Integer resolvePrice(Map<String, Object> data, Long productId) {
        Integer eventPrice = parseInteger(data.get("price"), null);
        if (eventPrice != null) return eventPrice;
        return productRepository.findById(productId)
                .map(Product::getPrice)
                .orElse(null);
    }

    private Integer parseInteger(Object raw, Integer defaultValue) {
        if (raw == null || String.valueOf(raw).equals("null") || String.valueOf(raw).isBlank()) return defaultValue;
        try { return Integer.parseInt(String.valueOf(raw)); } catch (NumberFormatException e) { return defaultValue; }
    }

    private void saveWardrobe(Map<String, Object> data) {
        try {
            Long customerId = parseCustomerId(data);
            if (customerId == null) { log.warn("옷장 저장 실패: customer_id 없음"); return; }
            wardrobeItemRepository.save(WardrobeItem.builder()
                    .customerId(customerId)
                    .category(String.valueOf(data.get("category")).toUpperCase())
                    .style(String.valueOf(data.get("style")).toUpperCase())
                    .warmth(data.get("warmth") != null ? Integer.parseInt(String.valueOf(data.get("warmth"))) : null)
                    .colorId(data.get("color_id") != null ? Long.parseLong(String.valueOf(data.get("color_id"))) : null)
                    .registeredDate(LocalDate.now())
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("옷장 저장 완료");
        } catch (Exception e) {
            log.error("옷장 저장 실패: {}", e.getMessage());
        }
    }

    private void saveBehaviorLog(Map<String, Object> data, String eventType) {
        try {
            Long customerId = parseCustomerId(data);
            if (customerId == null) { log.warn("행동 로그 저장 실패: customer_id 없음"); return; }

            Long itemId = parseProductId(data);

            behaviorLogRepository.save(BehaviorLog.builder()
                    .customerId(customerId)
                    .eventType(eventType)
                    .pageUrl(String.valueOf(data.getOrDefault("page_url", data.getOrDefault("pageUrl", ""))))
                    .itemId(itemId)
                    .duration(data.get("duration") != null ? Integer.parseInt(String.valueOf(data.get("duration"))) : null)
                    .scrollDepth(data.get("scroll_depth") != null ? Integer.parseInt(String.valueOf(data.get("scroll_depth"))) : null)
                    .createdAt(LocalDateTime.now())
                    .build());

            log.info("행동 로그 저장 완료: {}", eventType);
        } catch (Exception e) {
            log.error("행동 로그 저장 실패: {}", e.getMessage());
        }
    }

    private void updateLastLoginDate(Map<String, Object> data) {
        try {
            Long customerId = parseCustomerId(data);
            if (customerId == null) return;
            customerRepository.findById(customerId).ifPresent(customer -> {
                customer.setLastLoginDate(LocalDate.now());
                customerRepository.save(customer);
            });
        } catch (Exception e) {
            log.error("최근 로그인 날짜 업데이트 실패: {}", e.getMessage());
        }
    }
}
