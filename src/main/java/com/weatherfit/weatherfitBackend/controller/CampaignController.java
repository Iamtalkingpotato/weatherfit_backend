package com.weatherfit.weatherfitBackend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherfit.weatherfitBackend.domain.entity.Campaign;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import com.weatherfit.weatherfitBackend.domain.entity.Coupon;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.entity.CustomerCoupon;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignActionRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CouponRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerCouponRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.weatherfit.weatherfitBackend.domain.repository.PurchaseRepository;
import com.weatherfit.weatherfitBackend.domain.repository.TemperatureFeedbackRepository;
import com.weatherfit.weatherfitBackend.domain.repository.WardrobeItemRepository;
import com.weatherfit.weatherfitBackend.dto.CampaignExecuteRequest;
import com.weatherfit.weatherfitBackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CampaignController {

    private final CampaignRepository            campaignRepository;
    private final CampaignActionRepository      campaignActionRepository;
    private final CouponRepository              couponRepository;
    private final CustomerRepository            customerRepository;
    private final CustomerCouponRepository      customerCouponRepository;
    private final TemperatureFeedbackRepository temperatureFeedbackRepository;
    private final PurchaseRepository            purchaseRepository;
    private final WardrobeItemRepository        wardrobeItemRepository;
    private final ObjectMapper                  objectMapper;
    private final EmailService                  emailService;

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @GetMapping
    public List<Campaign> getAll() {
        List<Campaign> campaigns = campaignRepository.findAll();
        campaigns.forEach(c -> c.setActions(campaignActionRepository.findByCampaignId(c.getId())));
        return campaigns;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Campaign> getById(@PathVariable Long id) {
        return campaignRepository.findById(id)
                .map(c -> {
                    c.setActions(campaignActionRepository.findByCampaignId(c.getId()));
                    return ResponseEntity.ok(c);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Campaign> create(@RequestBody Map<String, Object> body) {
        Campaign campaign = new Campaign();
        applyCampaignPayload(campaign, body);
        Campaign saved = campaignRepository.save(campaign);
        saveActions(saved.getId(), body);
        saved.setActions(campaignActionRepository.findByCampaignId(saved.getId()));
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Campaign> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return campaignRepository.findById(id)
                .map(existing -> {
                    applyCampaignPayload(existing, body);
                    Campaign saved = campaignRepository.save(existing);
                    // 기존 액션 전부 교체
                    campaignActionRepository.deleteByCampaignId(saved.getId());
                    saveActions(saved.getId(), body);
                    saved.setActions(campaignActionRepository.findByCampaignId(saved.getId()));
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!campaignRepository.existsById(id)) return ResponseEntity.notFound().build();
        campaignActionRepository.deleteByCampaignId(id);
        campaignRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── 팝업 조회 ───────────────────────────────────────────────────────────────

    @GetMapping("/popup")
    public ResponseEntity<?> getPopup(@RequestParam Long customerId) {
        LocalDate today = LocalDate.now();

        return campaignRepository.findAll().stream()
                .filter(c -> c.getPopupMessage() != null && !c.getPopupMessage().isBlank())
                .filter(c -> "실행중".equals(c.getStatus()) || "진행중".equals(c.getStatus()))
                .filter(c -> c.getStartDate() != null && !today.isBefore(c.getStartDate()))
                .filter(c -> c.getEndDate()   != null && !today.isAfter(c.getEndDate()))
                .findFirst()
                .map(c -> ResponseEntity.ok((Object) Map.of("id", c.getId(), "popupMessage", c.getPopupMessage())))
                .orElse(ResponseEntity.ok(null));
    }

    // ── 쿠폰 수동 발급 ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/issue-coupons")
    public ResponseEntity<?> issueCoupons(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Campaign campaign = campaignRepository.findById(id).orElse(null);
        if (campaign == null) return ResponseEntity.notFound().build();

        List<Long> effectiveCouponIds = getEffectiveCouponIds(id);
        if (effectiveCouponIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "캠페인에 연결된 쿠폰이 없습니다."));
        }

        List<Long> customerIds = extractCustomerIds(body);
        List<Map<String, Object>> filterConditions = extractFilterConditions(body);
        if (filterConditions.isEmpty()) {
            filterConditions = parseFilterConditions(campaign.getFilterConditions());
        }
        final List<Map<String, Object>> finalFilterConditions = filterConditions;

        boolean hasCustomerIds = !customerIds.isEmpty();
        boolean hasFilterConditions = !finalFilterConditions.isEmpty();

        if (!hasCustomerIds && !hasFilterConditions) {
            return ResponseEntity.badRequest().body(Map.of("error", "대상 고객이 없습니다."));
        }

        List<Long> targetIds;
        if (hasCustomerIds) {
            targetIds = new ArrayList<>(customerIds);
        } else {
            final Campaign finalCampaign = campaign;
            targetIds = customerRepository.findAll().stream()
                    .filter(c -> matchesConditions(c, finalFilterConditions, finalCampaign))
                    .map(Customer::getId)
                    .collect(Collectors.toList());
        }

        Set<Long> distinctTargetIds = new LinkedHashSet<>(targetIds);

        int totalIssued = 0;
        int totalSkipped = 0;

        for (Long couponId : effectiveCouponIds) {
            Coupon coupon = couponRepository.findById(couponId).orElse(null);
            if (coupon == null) continue;

            int issuedForThisCoupon = 0;
            for (Long customerId : distinctTargetIds) {
                List<CustomerCoupon> existing = customerCouponRepository.findByCustomerId(customerId);
                boolean alreadyIssued = existing.stream()
                        .anyMatch(cc -> cc.getCouponId() != null && cc.getCouponId().equals(coupon.getId()));
                if (alreadyIssued) { totalSkipped++; continue; }

                LocalDateTime now = LocalDateTime.now();
                customerCouponRepository.save(CustomerCoupon.builder()
                        .customerId(customerId)
                        .couponId(coupon.getId())
                        .issuedAt(now)
                        .expiredAt(now.plusDays(coupon.getValidDays() != null ? coupon.getValidDays() : 30))
                        .status("ISSUED")
                        .build());
                issuedForThisCoupon++;
                totalIssued++;
            }

            if (issuedForThisCoupon > 0) {
                coupon.setIssuedCount((coupon.getIssuedCount() == null ? 0 : coupon.getIssuedCount()) + issuedForThisCoupon);
                couponRepository.save(coupon);
            }
        }

        return ResponseEntity.ok(Map.of(
                "issuedCount", totalIssued,
                "skippedCount", totalSkipped,
                "message", totalIssued + "명에게 쿠폰이 발급되었습니다."
        ));
    }

    // ── 액션 저장 헬퍼 ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void saveActions(Long campaignId, Map<String, Object> body) {
        Object raw = body.get("actions");
        if (!(raw instanceof List<?> list)) return;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> actionMap = (Map<String, Object>) m;

            String actionType = asString(actionMap.get("actionType"));
            if (actionType == null || actionType.isBlank()) continue;

            CampaignAction action = CampaignAction.builder()
                    .campaignId(campaignId)
                    .actionType(actionType.toUpperCase())
                    .couponId(asLong(actionMap.get("couponId")))
                    .popupMessage(asString(actionMap.get("popupMessage")))
                    .smsMessage(asString(actionMap.get("smsMessage")))
                    .build();
            campaignActionRepository.save(action);
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

    // ── campaign payload 적용 ────────────────────────────────────────────────────

    private void applyCampaignPayload(Campaign target, Map<String, Object> body) {
        if (body.containsKey("campaignName"))    target.setCampaignName(asString(body.get("campaignName")));
        if (body.containsKey("description"))     target.setDescription(asString(body.get("description")));
        if (body.containsKey("category1"))       target.setCategory1(asString(body.get("category1")));
        if (body.containsKey("category2"))       target.setCategory2(asString(body.get("category2")));
        if (body.containsKey("status"))          target.setStatus(defaultIfBlank(asString(body.get("status")), "설계중"));
        if (body.containsKey("startDate"))       target.setStartDate(asLocalDate(body.get("startDate")));
        if (body.containsKey("endDate"))         target.setEndDate(asLocalDate(body.get("endDate")));
        if (body.containsKey("graceDays"))       target.setGraceDays(defaultIfNull(asInteger(body.get("graceDays")), 0));
        if (body.containsKey("customerType"))    target.setCustomerType(defaultIfBlank(asString(body.get("customerType")), "개인"));
        if (body.containsKey("visibility"))      target.setVisibility(defaultIfBlank(asString(body.get("visibility")), "비공개"));
        if (body.containsKey("tags"))            target.setTags(asString(body.get("tags")));
        if (body.containsKey("department"))      target.setDepartment(asString(body.get("department")));
        if (body.containsKey("createdBy"))       target.setCreatedBy(asString(body.get("createdBy")));
        if (body.containsKey("filterSubject"))   target.setFilterSubject(asString(body.get("filterSubject")));
        if (body.containsKey("filterConditions")) {
            target.setFilterConditions(toJsonText(body.get("filterConditions")));
        }
        if (body.containsKey("anonymousMinVisits")) {
            target.setAnonymousMinVisits(asInteger(body.get("anonymousMinVisits")));
        }
        if (body.containsKey("emailSubject"))    target.setEmailSubject(asString(body.get("emailSubject")));
        if (body.containsKey("emailBody"))       target.setEmailBody(asString(body.get("emailBody")));
        if (body.containsKey("popupMessage"))    target.setPopupMessage(asString(body.get("popupMessage")));
    }

    private String toJsonText(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Long> extractCustomerIds(Map<String, Object> body) {
        Object raw = body.get("customerIds");
        if (!(raw instanceof List<?> list)) return Collections.emptyList();

        List<Long> result = new ArrayList<>();
        for (Object v : list) {
            Long cid = asLong(v);
            if (cid != null) result.add(cid);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFilterConditions(Map<String, Object> body) {
        Object raw = body.get("filterConditions");
        if (raw == null) return Collections.emptyList();
        if (raw instanceof String s) return parseFilterConditions(s);
        if (!(raw instanceof List<?> list)) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) result.add((Map<String, Object>) item);
        }
        return result;
    }

    private List<Map<String, Object>> parseFilterConditions(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ── 필터 매칭 ────────────────────────────────────────────────────────────────

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
            return switch (type) {
                case "number"           -> matchNumber(getNumberField(c, field, cond, campaign), operator, Double.parseDouble(String.valueOf(value)));
                case "boolean"          -> matchBoolean(getBooleanField(c, field), operator, asBoolean(value));
                case "date"             -> matchDate(getDateField(c, field), operator, String.valueOf(value));
                case "select", "string" -> matchString(getStringField(c, field), operator, String.valueOf(value));
                default                 -> matchString(getStringField(c, field), operator, String.valueOf(value));
            };
        } catch (Exception e) {
            return false;
        }
    }

    // ── Customer 필드 값 추출 ────────────────────────────────────────────────────

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
        java.time.LocalDate from = useCampaignStart
                ? campaign.getStartDate() : null;

        return switch (field) {
            case "id"              -> id != null ? id.doubleValue() : null;
            case "coldSensitivity" -> c.getColdSensitivity() != null ? c.getColdSensitivity().doubleValue() : null;
            case "hotFeedbackCount"  -> useCampaignStart
                    ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackAndFeedbackDateGreaterThanEqual(id, "HOT", from)
                    : (double) temperatureFeedbackRepository.countByCustomerIdAndFeedback(id, "HOT");
            case "coldFeedbackCount" -> useCampaignStart
                    ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackAndFeedbackDateGreaterThanEqual(id, "COLD", from)
                    : (double) temperatureFeedbackRepository.countByCustomerIdAndFeedback(id, "COLD");
            case "feedbackCount"     -> useCampaignStart
                    ? (double) temperatureFeedbackRepository.countByCustomerIdAndFeedbackDateGreaterThanEqual(id, from)
                    : (double) temperatureFeedbackRepository.countByCustomerId(id);
            case "purchaseCount"     -> (double) purchaseRepository.findByCustomerIdAndStatus(id, "PURCHASED").size();
            case "totalPurchaseAmount" -> {
                Integer sum = purchaseRepository.sumPurchasedAmountByCustomerId(id);
                yield sum != null ? sum.doubleValue() : 0.0;
            }
            case "cartCount"     -> (double) purchaseRepository.findByCustomerIdAndStatus(id, "CART").size();
            case "wishlistCount" -> (double) purchaseRepository.findByCustomerIdAndStatus(id, "WISHLIST").size();
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
        return switch (operator) {
            case "neq" -> fieldVal != condVal;
            default    -> fieldVal.equals(condVal);
        };
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

    // ── 변환 유틸 ─────────────────────────────────────────────────────────────────

    private String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return "null".equals(s) ? null : s;
    }

    private Integer asInteger(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Long asLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Boolean asBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("1".equals(s) || "true".equals(s) || "y".equals(s) || "yes".equals(s)) return true;
        if ("0".equals(s) || "false".equals(s) || "n".equals(s) || "no".equals(s)) return false;
        return null;
    }

    private LocalDate asLocalDate(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        String s = String.valueOf(v);
        try { return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s); } catch (Exception e) { return null; }
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
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

    // ── Execute ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/execute")
    public ResponseEntity<?> execute(
            @PathVariable Long id,
            @RequestBody CampaignExecuteRequest req) {

        campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        List<Customer> targets = customerRepository.findAllEmailConsentTrue();

        int successCount = 0;
        int failCount = 0;
        for (Customer customer : targets) {
            try {
                emailService.sendEmail(customer.getEmail(), req.getEmailSubject(), req.getEmailBody());
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "campaignId", id,
                "totalTargets", targets.size(),
                "successCount", successCount,
                "failCount", failCount
        ));
    }
}
