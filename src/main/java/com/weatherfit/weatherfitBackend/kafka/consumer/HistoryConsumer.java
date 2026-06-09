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
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final AnonymousPendingActionRepository anonymousPendingActionRepository;
    private final AnonymousUserRepository anonymousUserRepository;
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

            // 비로그인 사용자(uid_ 접두사)의 action은 pending 큐에 저장
            String rawCustomerId = String.valueOf(data.getOrDefault("customer_id", ""));
            boolean isAnonymous = rawCustomerId.startsWith("uid_") && customerId == null;
            if (isAnonymous) {
                saveAnonymousPending(rawCustomerId, eventType, message);
            }

            switch (eventType) {
                case "SIGNUP"                       -> saveCustomer(data);
                case "FEEDBACK"                     -> saveFeedback(data);
                case "PURCHASE", "WISHLIST", "CART" -> savePurchase(data);
                case "WARDROBE"                     -> saveWardrobe(data);
                case "VIEW", "SCROLL"               -> saveBehaviorLog(data, eventType);
                case "LOGIN" -> {
                    saveBehaviorLog(data, eventType);
                    linkAnonymousOnLogin(data);
                }
                case "add_to_cart", "wishlist_add", "purchase" -> {
                    if (!isAnonymous) {
                        savePurchase(data);
                        saveBehaviorLog(data, eventType);
                    }
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
        // 정규화된 타입 기준으로 status 결정 (raw WISHLIST_ADD도 WISHLIST로 처리)
        String normalized = normalizeEventType(data);
        String rawType    = String.valueOf(data.getOrDefault("event_type", ""));
        String status;
        if ("wishlist_add".equals(normalized) || "WISHLIST".equals(rawType) || "WISHLIST_ADD".equals(rawType)) {
            status = "WISHLIST";
        } else if ("add_to_cart".equals(normalized) || "CART".equals(rawType) || "ADD_TO_CART".equals(rawType)) {
            status = "CART";
        } else {
            status = "PURCHASED";
        }

        Long cid;
        try {
            cid = Long.parseLong(String.valueOf(customerId));
        } catch (NumberFormatException e) {
            log.warn("구매 저장 실패: customer_id 파싱 불가 ({})", customerId);
            return;
        }
        Long pid;
        try {
            pid = Long.parseLong(String.valueOf(data.get("product_id")));
        } catch (NumberFormatException e) {
            log.warn("구매 저장 실패: product_id 파싱 불가");
            return;
        }

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

    /** 비로그인 액션을 pending 테이블에 임시 저장 */
    private void saveAnonymousPending(String anonymousId, String eventType, String rawMessage) {
        try {
            anonymousPendingActionRepository.save(AnonymousPendingAction.builder()
                    .anonymousId(anonymousId)
                    .eventType(eventType)
                    .dataJson(rawMessage)
                    .build());
            log.info("비로그인 액션 임시 저장: anonymousId={}, eventType={}", anonymousId, eventType);
        } catch (Exception e) {
            log.warn("비로그인 액션 저장 실패: {}", e.getMessage());
        }
    }

    /** 로그인 이벤트에 anonymous_id가 있으면 pending 액션을 실제 고객 데이터로 이관 */
    private void linkAnonymousOnLogin(Map<String, Object> data) {
        try {
            Object rawAnonymousId = data.get("anonymous_id");
            if (rawAnonymousId == null || String.valueOf(rawAnonymousId).isBlank()) return;
            String anonymousId = String.valueOf(rawAnonymousId);

            Long customerId = parseCustomerId(data);
            if (customerId == null) return;

            // 1. 기존 테이블에 anonymous_id 컬럼이 있는 경우 직접 연동 (이미 저장된 데이터)
            try { anonymousUserRepository.linkPurchases(customerId, anonymousId); } catch (Exception ignored) {}
            try { anonymousUserRepository.linkWardrobeItems(customerId, anonymousId); } catch (Exception ignored) {}
            try { anonymousUserRepository.linkFeedbacks(customerId, anonymousId); } catch (Exception ignored) {}

            // 2. pending 큐에 저장된 임시 액션 이관
            List<AnonymousPendingAction> pending =
                    anonymousPendingActionRepository.findByAnonymousId(anonymousId);

            for (AnonymousPendingAction action : pending) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> actionData = objectMapper.readValue(action.getDataJson(), Map.class);
                    actionData.put("customer_id", customerId.toString());

                    String et = action.getEventType();

                    // 행동 로그 복원
                    Long itemId = parseProductId(actionData);
                    behaviorLogRepository.save(BehaviorLog.builder()
                            .customerId(customerId)
                            .eventType(et)
                            .pageUrl(String.valueOf(actionData.getOrDefault("page_url",
                                    actionData.getOrDefault("pageUrl", ""))))
                            .itemId(itemId)
                            .duration(actionData.get("duration") != null
                                    ? Integer.parseInt(String.valueOf(actionData.get("duration"))) : null)
                            .scrollDepth(actionData.get("scroll_depth") != null
                                    ? Integer.parseInt(String.valueOf(actionData.get("scroll_depth"))) : null)
                            .createdAt(action.getCreatedAt())
                            .build());

                    // 구매/찜/장바구니 복원
                    if (Set.of("wishlist_add", "add_to_cart", "purchase",
                            "WISHLIST", "CART", "PURCHASE").contains(et)) {
                        savePurchase(actionData);
                    }
                } catch (Exception e) {
                    log.warn("비로그인 액션 이관 실패 (id={}): {}", action.getId(), e.getMessage());
                }
            }

            if (!pending.isEmpty()) {
                anonymousPendingActionRepository.deleteByAnonymousId(anonymousId);
            }

            // AnonymousUser converted 플래그 업데이트
            anonymousUserRepository.findByAnonymousId(anonymousId).ifPresent(user -> {
                user.setConverted(true);
                anonymousUserRepository.save(user);
            });

            log.info("비로그인 → 로그인 연동 완료: anonymousId={}, customerId={}, pending={}건",
                    anonymousId, customerId, pending.size());
        } catch (Exception e) {
            log.error("비로그인 연동 실패: {}", e.getMessage());
        }
    }

    /** weatherfit.customer 토픽 소비 — changed_fields에 있는 필드만 선택적 업데이트 */
    @KafkaListener(topics = "weatherfit.customer", groupId = SUCCESS_GROUP)
    public void consumeCustomerUpdate(ConsumerRecord<String, String> record) {
        String message = record.value();
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String eventType = String.valueOf(data.getOrDefault("event_type", ""));
            if (!"customer_update".equals(eventType)) return;

            Long customerId = parseCustomerId(data);
            if (customerId == null) { log.warn("customer_update 무시: customer_id 없음"); return; }

            Object changedFieldsRaw = data.get("changed_fields");
            if (!(changedFieldsRaw instanceof Map)) { log.warn("customer_update 무시: changed_fields 없음"); return; }

            @SuppressWarnings("unchecked")
            Map<String, Object> changedFields = (Map<String, Object>) changedFieldsRaw;

            customerRepository.findById(customerId).ifPresentOrElse(customer -> {
                if (changedFields.containsKey("marketingConsent") && changedFields.get("marketingConsent") != null)
                    customer.setMarketingConsent(Boolean.parseBoolean(String.valueOf(changedFields.get("marketingConsent"))));
                if (changedFields.containsKey("emailConsent") && changedFields.get("emailConsent") != null)
                    customer.setEmailConsent(Boolean.parseBoolean(String.valueOf(changedFields.get("emailConsent"))));
                if (changedFields.containsKey("coldSensitivity") && changedFields.get("coldSensitivity") != null)
                    customer.setColdSensitivity(Integer.parseInt(String.valueOf(changedFields.get("coldSensitivity"))));
                if (changedFields.containsKey("activityLevel") && changedFields.get("activityLevel") != null)
                    customer.setActivityLevel(String.valueOf(changedFields.get("activityLevel")));
                if (changedFields.containsKey("preferredStyle") && changedFields.get("preferredStyle") != null)
                    customer.setPreferredStyle(String.valueOf(changedFields.get("preferredStyle")));
                customerRepository.save(customer);
                log.info("고객 정보 업데이트 완료: customerId={}, 변경필드={}", customerId, changedFields.keySet());
            }, () -> log.warn("customer_update 무시: 고객 없음 customerId={}", customerId));

        } catch (Exception e) {
            log.error("고객 정보 업데이트 실패: {}", e.getMessage());
        }
    }
}
