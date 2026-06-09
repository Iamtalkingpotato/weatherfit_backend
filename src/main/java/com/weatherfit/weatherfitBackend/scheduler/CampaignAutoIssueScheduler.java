package com.weatherfit.weatherfitBackend.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherfit.weatherfitBackend.domain.entity.Campaign;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignEmailLog;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignPopupLog;
import com.weatherfit.weatherfitBackend.domain.entity.Coupon;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.entity.CustomerCoupon;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignActionRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignEmailLogRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignPopupLogRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CouponRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerCouponRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.weatherfit.weatherfitBackend.domain.repository.PurchaseRepository;
import com.weatherfit.weatherfitBackend.domain.repository.TemperatureFeedbackRepository;
import com.weatherfit.weatherfitBackend.domain.repository.WardrobeItemRepository;
import com.weatherfit.weatherfitBackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignAutoIssueScheduler {

    private static final String EVENT_GROUP = "weatherfit-campaign-consumer";
    private static final Set<String> BEHAVIOR_TRIGGERS =
            Set.of("login", "wardrobe_add", "wishlist_add", "wishlist_remove");

    private final CampaignRepository            campaignRepository;
    private final CampaignActionRepository      campaignActionRepository;
    private final CampaignEmailLogRepository    campaignEmailLogRepository;
    private final CampaignPopupLogRepository    campaignPopupLogRepository;
    private final CouponRepository              couponRepository;
    private final CustomerRepository            customerRepository;
    private final CustomerCouponRepository      customerCouponRepository;
    private final TemperatureFeedbackRepository temperatureFeedbackRepository;
    private final PurchaseRepository            purchaseRepository;
    private final WardrobeItemRepository        wardrobeItemRepository;
    private final ObjectMapper                  objectMapper;
    private final EmailService                  emailService;

    /** 1분마다 캠페인 상태를 날짜 기준으로 자동 갱신하고, 실행 중인 캠페인의 쿠폰을 자동 발급한다. */
    @Scheduled(fixedDelay = 60_000)
    public void autoManageCampaigns() {
        LocalDate today = LocalDate.now();
        List<Campaign> allCampaigns = campaignRepository.findAll();

        // ── 1. 상태 자동 전환 ─────────────────────────────────────────────────
        for (Campaign c : allCampaigns) {
            String current = c.getStatus();
            LocalDate start = c.getStartDate();
            LocalDate end   = c.getEndDate();

            // 종료일 지남 → 실행완료
            if (end != null && today.isAfter(end) && !"실행완료".equals(current)) {
                c.setStatus("실행완료");
                campaignRepository.save(c);
                log.info("[상태전환] 캠페인={} → 실행완료", c.getId());
                continue;
            }

            // 시작일 도래 + 종료 전 → 실행중 (정지중이면 유지)
            boolean inPeriod = (start == null || !today.isBefore(start))
                            && (end   == null || !today.isAfter(end));
            if (inPeriod
                    && !"실행중".equals(current)
                    && !"실행정지중".equals(current)
                    && !"실행완료".equals(current)) {
                c.setStatus("실행중");
                campaignRepository.save(c);
                log.info("[상태전환] 캠페인={} → 실행중", c.getId());
            }
        }

        // ── 2. 실행중 캠페인 전체 고객 일괄 체크 (스케줄러 보완용) ──
        List<Customer> allCustomers = customerRepository.findAll();
        for (Customer customer : allCustomers) {
            checkCampaignForCustomer(customer.getId());
        }
    }

    /** campaign_action 테이블에서 COUPON 액션의 쿠폰 ID 목록 반환 */
    private List<Long> getEffectiveCouponIds(Long campaignId) {
        return campaignActionRepository.findByCampaignId(campaignId).stream()
                .filter(a -> "COUPON".equalsIgnoreCase(a.getActionType()))
                .filter(a -> a.getCouponId() != null)
                .map(CampaignAction::getCouponId)
                .collect(Collectors.toList());
    }

    // ── 필터 조건 파싱 ────────────────────────────────────────────────────────────

    private List<Map<String, Object>> parseConditions(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("filterConditions JSON 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── 필터 매칭 ─────────────────────────────────────────────────────────────────

    private boolean matchesConditions(Customer c, List<Map<String, Object>> conditions, Campaign campaign) {
        for (Map<String, Object> cond : conditions) {
            if (!matchesCondition(c, cond, campaign)) return false;
        }
        return true;
    }

    private boolean matchesCondition(Customer c, Map<String, Object> cond, Campaign campaign) {
        String field    = firstString(cond, "field", "fieldId");
        String type     = defaultIfBlank(firstString(cond, "type", "dataType"), "string");
        String operator = normalizeOperator(defaultIfBlank(firstString(cond, "operator"), "="));
        Object value    = cond.get("value");

        if (field == null || field.isBlank()) return false;
        if (value == null || String.valueOf(value).isBlank()) return true;

        try {
            Object actualValue;
            boolean result;

            if ("number".equals(type)) {
                Double numActual  = getNumberField(c, field, cond, campaign);
                double numExpected = Double.parseDouble(String.valueOf(value));
                result      = matchNumber(numActual, operator, numExpected);
                actualValue = numActual;
            } else if ("boolean".equals(type)) {
                Boolean boolActual = getBooleanField(c, field);
                result      = matchBoolean(boolActual, operator, asBoolean(value));
                actualValue = boolActual;
            } else if ("date".equals(type)) {
                LocalDate dateActual = getDateField(c, field);
                result      = matchDate(dateActual, operator, String.valueOf(value));
                actualValue = dateActual;
            } else if ("select".equals(type)) {
                String strActual = getStringField(c, field);
                // true/false 값 정규화: "true" == "동의", DB boolean은 "true"/"false" 문자열로 비교
                String condStr = String.valueOf(value);
                result      = matchString(strActual, operator, condStr);
                actualValue = strActual;
            } else {
                String strActual = getStringField(c, field);
                result      = matchString(strActual, operator, String.valueOf(value));
                actualValue = strActual;
            }

            log.debug("[필터매칭] 고객={} field={} → 실제값={} / 기준값={} / 결과={}",
                    c.getId(), field, actualValue, value, result);
            return result;
        } catch (Exception e) {
            log.warn("[필터매칭] 고객={} field={} type={} op={} 조건 평가 중 예외: {}",
                    c.getId(), field, type, operator, e.getMessage(), e);
            return false;
        }
    }

    // ── Customer 필드 값 추출 ─────────────────────────────────────────────────────

    private String getStringField(Customer c, String field) {
        return switch (field) {
            case "name"            -> c.getName();
            case "email"           -> c.getEmail();
            case "phone"           -> c.getPhone();
            case "gender"          -> c.getGender();
            case "membershipLevel" -> c.getMembershipLevel();
            case "activityLevel"   -> c.getActivityLevel();
            case "preferredStyle"  -> c.getPreferredStyle();
            case "joinChannel"     -> c.getJoinChannel();
            case "joinType"        -> c.getJoinType();
            case "uid"             -> c.getUid();
            case "coldSensitivity"   -> c.getColdSensitivity()   != null ? String.valueOf(c.getColdSensitivity())   : null;
            case "emailConsent"      -> c.getEmailConsent()      != null ? String.valueOf(c.getEmailConsent())      : null;
            case "marketingConsent"  -> c.getMarketingConsent()  != null ? String.valueOf(c.getMarketingConsent())  : null;
            case "pushConsent"       -> c.getPushConsent()        != null ? String.valueOf(c.getPushConsent())        : null;
            case "smsConsent"        -> c.getSmsConsent()         != null ? String.valueOf(c.getSmsConsent())         : null;
            case "isFraud"           -> c.getIsFraud()            != null ? String.valueOf(c.getIsFraud())            : null;
            default                  -> null;
        };
    }

    private Double getNumberField(Customer c, String field, Map<String, Object> cond, Campaign campaign) {
        Long id = c.getId();
        String since = firstString(cond, "since");
        boolean useCampaignStart = "campaign_start".equals(since)
                && campaign != null && campaign.getStartDate() != null;
        LocalDate from = useCampaignStart ? campaign.getStartDate() : null;

        log.debug("[getNumberField] 고객={} field={} since='{}' useCampaignStart={} from={}",
                id, field, since, useCampaignStart, from);

        return switch (field) {
            case "id"              -> id != null ? id.doubleValue() : null;
            case "coldSensitivity" -> c.getColdSensitivity() != null ? c.getColdSensitivity().doubleValue() : null;
            case "hotFeedbackCount"  -> {
                double cnt = useCampaignStart
                        ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackAndFeedbackDateGreaterThanEqual(id, "HOT", from)
                        : (double) temperatureFeedbackRepository.countByCustomerIdAndFeedback(id, "HOT");
                log.debug("[getNumberField] 고객={} hotFeedbackCount={} (from={})", id, cnt, from);
                yield cnt;
            }
            case "coldFeedbackCount" -> useCampaignStart
                    ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackAndFeedbackDateGreaterThanEqual(id, "COLD", from)
                    : (double) temperatureFeedbackRepository.countByCustomerIdAndFeedback(id, "COLD");
            case "feedbackCount"     -> useCampaignStart
                    ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackDateGreaterThanEqual(id, from)
                    : (double) temperatureFeedbackRepository.countByCustomerId(id);
            case "purchaseCount"     -> useCampaignStart
                    ? (double) purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(id, "PURCHASED", from.atStartOfDay()).size()
                    : (double) purchaseRepository.findByCustomerIdAndStatus(id, "PURCHASED").size();
            case "totalPurchaseAmount" -> {
                if (useCampaignStart) {
                    yield purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(id, "PURCHASED", from.atStartOfDay())
                            .stream().mapToDouble(p -> p.getPrice() != null ? p.getPrice() : 0).sum();
                }
                Integer sum = purchaseRepository.sumPurchasedAmountByCustomerId(id);
                yield sum != null ? sum.doubleValue() : 0.0;
            }
            case "cartCount"     -> useCampaignStart
                    ? (double) purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(id, "CART", from.atStartOfDay()).size()
                    : (double) purchaseRepository.findByCustomerIdAndStatus(id, "CART").size();
            case "wishlistCount" -> useCampaignStart
                    ? (double) purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(id, "WISHLIST", from.atStartOfDay()).size()
                    : (double) purchaseRepository.findByCustomerIdAndStatus(id, "WISHLIST").size();
            case "wardrobeCount"           -> (double) wardrobeItemRepository.countByCustomerId(id);
            case "wardrobeOuterCount"      -> (double) wardrobeItemRepository.countByCustomerIdAndCategory(id, "OUTER");
            case "wardrobeTopCount"        -> (double) wardrobeItemRepository.countByCustomerIdAndCategory(id, "TOP");
            case "wardrobeBottomCount"     -> (double) wardrobeItemRepository.countByCustomerIdAndCategory(id, "BOTTOM");
            case "wardrobeAccessoryCount"  -> (double) wardrobeItemRepository.countByCustomerIdAndCategory(id, "ACCESSORY");
            default -> null;
        };
    }

    private Boolean getBooleanField(Customer c, String field) {
        return switch (field) {
            case "marketingConsent" -> c.getMarketingConsent();
            case "pushConsent"      -> c.getPushConsent();
            case "emailConsent"     -> c.getEmailConsent();
            case "smsConsent"       -> c.getSmsConsent();
            case "dmConsent"        -> c.getDmConsent();
            case "tmConsent"        -> c.getTmConsent();
            case "isFraud"          -> c.getIsFraud();
            default                 -> null;
        };
    }

    private LocalDate getDateField(Customer c, String field) {
        return switch (field) {
            case "birthDate"     -> c.getBirthDate();
            case "joinDate"      -> c.getJoinDate();
            case "lastLoginDate" -> c.getLastLoginDate();
            default              -> null;
        };
    }

    // ── 타입별 비교 연산 ──────────────────────────────────────────────────────────

    private boolean matchString(String fieldVal, String operator, String condVal) {
        if (fieldVal == null) return false;
        return switch (operator) {
            case "eq"       -> fieldVal.equals(condVal);
            case "neq"      -> !fieldVal.equals(condVal);
            case "contains" -> fieldVal.contains(condVal);
            case "in"       -> List.of(condVal.split(",")).contains(fieldVal);
            default         -> fieldVal.equals(condVal);
        };
    }

    private boolean matchNumber(Double fieldVal, String operator, double condVal) {
        if (fieldVal == null) return false;
        return switch (operator) {
            case "eq"  -> fieldVal == condVal;
            case "neq" -> fieldVal != condVal;
            case "gt"  -> fieldVal > condVal;
            case "gte" -> fieldVal >= condVal;
            case "lt"  -> fieldVal < condVal;
            case "lte" -> fieldVal <= condVal;
            default    -> fieldVal == condVal;
        };
    }

    private boolean matchBoolean(Boolean fieldVal, String operator, Boolean condVal) {
        if (fieldVal == null || condVal == null) return false;
        return "neq".equals(operator) ? fieldVal != condVal : fieldVal.equals(condVal);
    }

    private boolean matchDate(LocalDate fieldVal, String operator, String condVal) {
        if (fieldVal == null) return false;
        LocalDate condDate = LocalDate.parse(condVal.length() > 10 ? condVal.substring(0, 10) : condVal);
        return switch (operator) {
            case "eq"  -> fieldVal.isEqual(condDate);
            case "neq" -> !fieldVal.isEqual(condDate);
            case "gt"  -> fieldVal.isAfter(condDate);
            case "gte" -> !fieldVal.isBefore(condDate);
            case "lt"  -> fieldVal.isBefore(condDate);
            case "lte" -> !fieldVal.isAfter(condDate);
            default    -> fieldVal.isEqual(condDate);
        };
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────────

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Boolean asBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("1".equals(s) || "true".equals(s)) return true;
        if ("0".equals(s) || "false".equals(s)) return false;
        return null;
    }

    private String normalizeOperator(String op) {
        return switch (op) {
            case "=", "eq"                  -> "eq";
            case "!=", "neq"                -> "neq";
            case "LIKE", "like", "contains" -> "contains";
            case "IN", "in"                 -> "in";
            case ">", "gt"                  -> "gt";
            case ">=", "gte"                -> "gte";
            case "<", "lt"                  -> "lt";
            case "<=", "lte"                -> "lte";
            default                         -> "eq";
        };
    }

    // ── 이벤트 드리븐 Kafka 리스너 ────────────────────────────────────────────────

    @KafkaListener(topics = "weatherfit.purchase", groupId = EVENT_GROUP)
    public void onPurchase(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> data = objectMapper.readValue(record.value(), Map.class);
            Long customerId = parseCustomerId(data);
            if (customerId != null) checkCampaignForCustomer(customerId);
        } catch (Exception e) {
            log.warn("[이벤트드리븐] purchase 파싱 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "weatherfit.feedback", groupId = EVENT_GROUP)
    public void onFeedback(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> data = objectMapper.readValue(record.value(), Map.class);
            Long customerId = parseCustomerId(data);
            if (customerId != null) checkCampaignForCustomer(customerId);
        } catch (Exception e) {
            log.warn("[이벤트드리븐] feedback 파싱 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "weatherfit.behavior", groupId = EVENT_GROUP)
    public void onBehavior(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> data = objectMapper.readValue(record.value(), Map.class);
            String eventType = String.valueOf(
                    data.getOrDefault("event_type", data.getOrDefault("action", ""))).toLowerCase();
            if (!BEHAVIOR_TRIGGERS.contains(eventType)) return;
            Long customerId = parseCustomerId(data);
            if (customerId != null) checkCampaignForCustomer(customerId);
        } catch (Exception e) {
            log.warn("[이벤트드리븐] behavior 파싱 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "weatherfit.customer", groupId = EVENT_GROUP)
    public void onCustomer(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> data = objectMapper.readValue(record.value(), Map.class);
            Long customerId = parseCustomerId(data);
            if (customerId != null) checkCampaignForCustomer(customerId);
        } catch (Exception e) {
            log.warn("[이벤트드리븐] customer 파싱 실패: {}", e.getMessage());
        }
    }

    /** 특정 고객 1명에 대해 실행 중인 모든 캠페인 조건을 체크하고, 조건 충족 시 쿠폰 발급 / 이메일 발송 / 팝업 세팅 */
    private void checkCampaignForCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) return;

        LocalDate today = LocalDate.now();
        List<Campaign> activeCampaigns = campaignRepository.findAll().stream()
                .filter(c -> "실행중".equals(c.getStatus()))
                .filter(c -> {
                    LocalDate start = c.getStartDate();
                    LocalDate end   = c.getEndDate();
                    return (start == null || !today.isBefore(start))
                        && (end   == null || !today.isAfter(end));
                })
                .filter(c -> c.getFilterConditions() != null && !c.getFilterConditions().isBlank())
                .toList();

        for (Campaign campaign : activeCampaigns) {
            log.debug("[이벤트드리븐] 고객={} 캠페인={} 체크 시작", customerId, campaign.getId());

            List<Map<String, Object>> conditions = parseConditions(campaign.getFilterConditions());
            if (!matchesConditions(customer, conditions, campaign)) continue;

            int couponCount = 0;
            int emailCount  = 0;
            int popupCount  = 0;

            // 쿠폰 발급
            List<Long> couponIds = getEffectiveCouponIds(campaign.getId());
            for (Long couponId : couponIds) {
                Coupon coupon = couponRepository.findById(couponId).orElse(null);
                if (coupon == null) continue;

                boolean alreadyIssued = customerCouponRepository.findByCustomerId(customerId).stream()
                        .anyMatch(cc -> coupon.getId().equals(cc.getCouponId()));
                if (alreadyIssued) continue;

                LocalDateTime now = LocalDateTime.now();
                customerCouponRepository.save(CustomerCoupon.builder()
                        .customerId(customerId)
                        .couponId(coupon.getId())
                        .issuedAt(now)
                        .expiredAt(now.plusDays(coupon.getValidDays() != null ? coupon.getValidDays() : 30))
                        .status("ISSUED")
                        .build());
                coupon.setIssuedCount((coupon.getIssuedCount() == null ? 0 : coupon.getIssuedCount()) + 1);
                couponRepository.save(coupon);
                couponCount++;
            }

            // 이메일 발송
            if (campaign.getEmailSubject() != null && !campaign.getEmailSubject().isBlank()
                    && campaign.getEmailBody() != null && !campaign.getEmailBody().isBlank()
                    && Boolean.TRUE.equals(customer.getEmailConsent())
                    && !campaignEmailLogRepository.existsByCampaignIdAndCustomerId(campaign.getId(), customerId)
                    && customer.getEmail() != null
                    && customer.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                try {
                    emailService.sendEmail(customer.getEmail(), campaign.getEmailSubject(), campaign.getEmailBody());
                    emailCount++;
                } catch (Exception e) {
                    log.warn("[이벤트드리븐] 이메일 발송 실패: 고객={} 캠페인={} — {}", customerId, campaign.getId(), e.getMessage());
                }
                // 성공·실패 모두 중복 방지를 위해 기록
                campaignEmailLogRepository.save(CampaignEmailLog.builder()
                        .campaignId(campaign.getId())
                        .customerId(customerId)
                        .sentAt(LocalDateTime.now())
                        .build());
            }

            // 팝업 액션 체크 — 오늘 날짜 기준 중복 체크 후 campaign_popup_log 저장
            boolean hasPopupAction = campaignActionRepository.findByCampaignId(campaign.getId()).stream()
                    .anyMatch(a -> "POPUP".equalsIgnoreCase(a.getActionType()));
            if (hasPopupAction) {
                LocalDate today2 = LocalDate.now();
                boolean alreadyShown = campaignPopupLogRepository
                        .existsByCampaignIdAndCustomerIdAndShownDate(campaign.getId(), customerId, today2);
                if (!alreadyShown) {
                    campaignPopupLogRepository.save(CampaignPopupLog.builder()
                            .campaignId(campaign.getId())
                            .customerId(customerId)
                            .shownDate(today2)
                            .createdAt(LocalDateTime.now())
                            .build());
                    popupCount++;
                }
            }

            if (couponCount + emailCount + popupCount > 0) {
                log.info("[이벤트드리븐] 고객={} 캠페인={} 완료 — 쿠폰={} / 이메일={} / 팝업={}",
                        customerId, campaign.getId(), couponCount, emailCount, popupCount);
            }
        }
    }

    private Long parseCustomerId(Map<String, Object> data) {
        Object raw = data.get("customer_id");
        if (raw == null || String.valueOf(raw).startsWith("uid_")) raw = data.get("partnerCustomerId");
        if (raw == null || String.valueOf(raw).equals("null")) return null;
        try { return Long.parseLong(String.valueOf(raw)); } catch (NumberFormatException e) { return null; }
    }
}
