package com.weatherfit.weatherfitBackend.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherfit.weatherfitBackend.domain.entity.Campaign;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignEmailLog;
import com.weatherfit.weatherfitBackend.domain.entity.Coupon;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.entity.CustomerCoupon;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignActionRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignEmailLogRepository;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignAutoIssueScheduler {

    private final CampaignRepository            campaignRepository;
    private final CampaignActionRepository      campaignActionRepository;
    private final CampaignEmailLogRepository    campaignEmailLogRepository;
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

        // ── 2. 실행중 캠페인 쿠폰 자동 발급 ──────────────────────────────────
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

        List<Customer> allCustomers = customerRepository.findAll();

        for (Campaign campaign : activeCampaigns) {
            List<Long> couponIds = getEffectiveCouponIds(campaign.getId());
            if (couponIds.isEmpty()) continue;

            List<Map<String, Object>> conditions = parseConditions(campaign.getFilterConditions());
            if (conditions.isEmpty()) continue;

            for (Long couponId : couponIds) {
                Coupon coupon = couponRepository.findById(couponId).orElse(null);
                if (coupon == null) continue;

                int issuedCount = 0;
                for (Customer customer : allCustomers) {
                    if (!matchesConditions(customer, conditions, campaign)) continue;

                    List<CustomerCoupon> existing = customerCouponRepository.findByCustomerId(customer.getId());
                    boolean alreadyIssued = existing.stream()
                            .anyMatch(cc -> coupon.getId().equals(cc.getCouponId()));
                    if (alreadyIssued) continue;

                    LocalDateTime now = LocalDateTime.now();
                    customerCouponRepository.save(CustomerCoupon.builder()
                            .customerId(customer.getId())
                            .couponId(coupon.getId())
                            .issuedAt(now)
                            .expiredAt(now.plusDays(coupon.getValidDays() != null ? coupon.getValidDays() : 30))
                            .status("ISSUED")
                            .build());
                    issuedCount++;
                    log.info("[자동발급] 캠페인={} 고객={} 쿠폰={}", campaign.getId(), customer.getId(), coupon.getId());
                }

                if (issuedCount > 0) {
                    coupon.setIssuedCount((coupon.getIssuedCount() == null ? 0 : coupon.getIssuedCount()) + issuedCount);
                    couponRepository.save(coupon);
                    log.info("[자동발급] 캠페인={} 쿠폰={} 완료: {}명", campaign.getId(), couponId, issuedCount);
                }
            }
        }

        // ── 3. 실행중 캠페인 이메일 자동 발송 ──────────────────────────────────
        List<Campaign> emailCampaigns = campaignRepository.findAll().stream()
                .filter(c -> "실행중".equals(c.getStatus()))
                .filter(c -> {
                    LocalDate start = c.getStartDate();
                    LocalDate end   = c.getEndDate();
                    return (start == null || !today.isBefore(start))
                        && (end   == null || !today.isAfter(end));
                })
                .filter(c -> c.getEmailSubject() != null && !c.getEmailSubject().isBlank())
                .filter(c -> c.getEmailBody()    != null && !c.getEmailBody().isBlank())
                .toList();

        log.info("[이메일발송] 대상 캠페인 {}개 발견", emailCampaigns.size());

        for (Campaign campaign : emailCampaigns) {
            log.info("[이메일발송] ── 캠페인={} 시작 (startDate={}, emailSubject='{}')",
                    campaign.getId(), campaign.getStartDate(), campaign.getEmailSubject());
            log.info("[이메일발송] filterConditions raw='{}'", campaign.getFilterConditions());

            List<Map<String, Object>> conditions = parseConditions(campaign.getFilterConditions());
            log.info("[이메일발송] 캠페인={} 파싱된 조건 {}개, 전체 고객 {}명",
                    campaign.getId(), conditions.size(), allCustomers.size());

            int sentCount   = 0;
            int skipFilter  = 0;
            int skipConsent = 0;
            int skipDup     = 0;

            for (Customer customer : allCustomers) {
                // 필터 조건이 있으면 조건 충족 고객만, 없으면 전체 대상
                if (!conditions.isEmpty() && !matchesConditions(customer, conditions, campaign)) {
                    skipFilter++;
                    continue;
                }

                // 이메일 수신 동의 고객만
                if (!Boolean.TRUE.equals(customer.getEmailConsent())) {
                    skipConsent++;
                    continue;
                }

                // 중복 발송 방지
                if (campaignEmailLogRepository.existsByCampaignIdAndCustomerId(
                        campaign.getId(), customer.getId())) {
                    skipDup++;
                    continue;
                }

                log.info("[이메일발송] 캠페인={} 고객={} email={} → 발송 시도",
                        campaign.getId(), customer.getId(), customer.getEmail());
                try {
                    emailService.sendEmail(
                            customer.getEmail(),
                            campaign.getEmailSubject(),
                            campaign.getEmailBody());

                    campaignEmailLogRepository.save(CampaignEmailLog.builder()
                            .campaignId(campaign.getId())
                            .customerId(customer.getId())
                            .sentAt(LocalDateTime.now())
                            .build());
                    sentCount++;
                    log.info("[이메일발송] 캠페인={} 고객={} 발송 성공", campaign.getId(), customer.getId());
                } catch (Exception e) {
                    log.warn("[이메일발송] 캠페인={} 고객={} 발송 실패: {}",
                            campaign.getId(), customer.getId(), e.getMessage(), e);
                }
            }

            log.info("[이메일발송] 캠페인={} 완료 — 발송={}명 / 필터탈락={}명 / 동의없음={}명 / 중복={}명",
                    campaign.getId(), sentCount, skipFilter, skipConsent, skipDup);
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
            } else {
                String strActual = getStringField(c, field);
                result      = matchString(strActual, operator, String.valueOf(value));
                actualValue = strActual;
            }

            log.info("[필터매칭] 고객={} field={} → 실제값={} / 기준값={} / 결과={}",
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
            case "coldSensitivity" -> c.getColdSensitivity() != null ? String.valueOf(c.getColdSensitivity()) : null;
            default                -> null;
        };
    }

    private Double getNumberField(Customer c, String field, Map<String, Object> cond, Campaign campaign) {
        Long id = c.getId();
        String since = firstString(cond, "since");
        boolean useCampaignStart = "campaign_start".equals(since)
                && campaign != null && campaign.getStartDate() != null;
        LocalDate from = useCampaignStart ? campaign.getStartDate() : null;

        log.info("[getNumberField] 고객={} field={} since='{}' useCampaignStart={} from={}",
                id, field, since, useCampaignStart, from);

        return switch (field) {
            case "id"              -> id != null ? id.doubleValue() : null;
            case "coldSensitivity" -> c.getColdSensitivity() != null ? c.getColdSensitivity().doubleValue() : null;
            case "hotFeedbackCount"  -> {
                double cnt = useCampaignStart
                        ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackAndFeedbackDateGreaterThanEqual(id, "HOT", from)
                        : (double) temperatureFeedbackRepository.countByCustomerIdAndFeedback(id, "HOT");
                log.info("[getNumberField] 고객={} hotFeedbackCount={} (from={})", id, cnt, from);
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
}
