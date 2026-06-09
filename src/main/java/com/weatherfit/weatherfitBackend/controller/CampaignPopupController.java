package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousPopupRule;
import com.weatherfit.weatherfitBackend.domain.entity.AnonymousUser;
import com.weatherfit.weatherfitBackend.domain.entity.Campaign;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousPopupRuleRepository;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousUserRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignActionRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.weatherfit.weatherfitBackend.domain.repository.PurchaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CampaignPopupController {

    private final AnonymousUserRepository      anonymousUserRepository;
    private final AnonymousPopupRuleRepository anonymousPopupRuleRepository;
    private final CampaignRepository           campaignRepository;
    private final CampaignActionRepository     campaignActionRepository;
    private final CustomerRepository           customerRepository;
    private final PurchaseRepository           purchaseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 비로그인 팝업 체크 ─────────────────────────────────────
    // GET /api/campaigns/popup-check?anonymousId=550e8400-xxxx
    @GetMapping("/popup-check")
    public ResponseEntity<Map<String, Object>> popupCheck(
            @RequestParam String anonymousId) {

        Map<String, Object> response = new HashMap<>();

        // 1. 해당 UUID 사용자 조회 — 없으면 신규 사용자로 insert 후 계속 진행
        Optional<AnonymousUser> userOpt = anonymousUserRepository.findByAnonymousId(anonymousId);
        AnonymousUser user;
        if (userOpt.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            user = AnonymousUser.builder()
                    .anonymousId(anonymousId)
                    .visitCount(1)
                    .firstVisit(now)
                    .lastVisit(now)
                    .popupShown(0)
                    .popupClicked(false)
                    .converted(false)
                    .build();
            anonymousUserRepository.save(user);
        } else {
            user = userOpt.get();
            user.setVisitCount(user.getVisitCount() + 1);
            user.setLastVisit(LocalDateTime.now());
            anonymousUserRepository.save(user);
        }

        // 2. visit_count 규칙으로 이미 팝업 노출됐거나 전환된 경우 스킵
        if ((user.getPopupShown() != null && user.getPopupShown() > 0) || Boolean.TRUE.equals(user.getConverted())) {
            response.put("showPopup", false);
            return ResponseEntity.ok(response);
        }

        // 3. 활성 팝업 규칙 중 조건에 맞는 첫 번째 규칙 탐색
        List<AnonymousPopupRule> rules = anonymousPopupRuleRepository.findByIsActiveTrue();
        AnonymousPopupRule matched = rules.stream()
                .filter(r -> {
                    if ("visit_count".equals(r.getRuleType())) {
                        return user.getVisitCount() >= r.getThreshold();
                    } else if ("repeat_interval".equals(r.getRuleType())) {
                        return user.getVisitCount() % r.getThreshold() == 0;
                    }
                    return false;
                })
                .findFirst().orElse(null);

        if (matched != null) {
            if ("visit_count".equals(matched.getRuleType())) {
                user.setPopupShown(user.getPopupShown() + 1);
                anonymousUserRepository.save(user);
            }
            response.put("showPopup", true);
            response.put("message", matched.getMessage());
            response.put("couponId", matched.getCouponId());
        } else {
            response.put("showPopup", false);
        }

        return ResponseEntity.ok(response);
    }

    // ── 로그인 고객 팝업 체크 ──────────────────────────────────
    // GET /api/campaigns/popup-check/customer?customerId=123
    @GetMapping("/popup-check/customer")
    public ResponseEntity<Map<String, Object>> popupCheckForCustomer(
            @RequestParam Long customerId) {

        Map<String, Object> response = new HashMap<>();

        // 1. 고객 조회
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            response.put("showPopup", false);
            return ResponseEntity.ok(response);
        }
        Customer customer = customerOpt.get();

        // 2. 진행중인 캠페인 중 POPUP 액션이 있는 것 탐색 (POPUP 액션 보유 여부까지 함께 확인)
        LocalDate today = LocalDate.now();
        CampaignAction foundPopupAction = null;
        Campaign foundCampaign = null;

        for (Campaign c : campaignRepository.findAll()) {
            if (!"실행중".equals(c.getStatus())) continue;
            if (c.getStartDate() == null || today.isBefore(c.getStartDate())) continue;
            if (c.getEndDate() == null || today.isAfter(c.getEndDate())) continue;
            if (!matchesFilter(c, customer)) continue;

            Optional<CampaignAction> popupAction = campaignActionRepository
                    .findByCampaignId(c.getId()).stream()
                    .filter(a -> "POPUP".equals(a.getActionType()))
                    .findFirst();
            if (popupAction.isPresent()) {
                foundCampaign = c;
                foundPopupAction = popupAction.get();
                break;
            }
        }

        if (foundPopupAction != null) {
            response.put("showPopup", true);
            response.put("campaignId", foundCampaign.getId());
            response.put("message", foundPopupAction.getPopupMessage() != null
                    ? foundPopupAction.getPopupMessage()
                    : "특별 혜택을 확인하세요!");
        } else {
            response.put("showPopup", false);
        }

        return ResponseEntity.ok(response);
    }

    // ── 필터 매칭 헬퍼 ────────────────────────────────────────
    private boolean matchesFilter(Campaign campaign, Customer customer) {
        try {
            String filterJson = campaign.getFilterConditions();
            if (filterJson == null || filterJson.isBlank() || filterJson.equals("[]")) return true;

            List<Map<String, Object>> conditions = objectMapper.readValue(filterJson, List.class);
            if (conditions.isEmpty()) return true;

            for (Map<String, Object> cond : conditions) {
                String fieldId   = String.valueOf(cond.get("fieldId"));
                String operator  = String.valueOf(cond.get("operator"));
                String value     = String.valueOf(cond.get("value"));
                String dateRange = cond.get("dateRange") != null ? String.valueOf(cond.get("dateRange")) : "all";
                if (!matchField(customer, campaign, fieldId, operator, value, dateRange)) return false;
            }
            return true;
        } catch (Exception e) {
            // 필터 평가 실패 시 안전하게 미표시 처리
            return false;
        }
    }

    private boolean matchField(Customer customer, Campaign campaign,
                               String fieldId, String operator, String value, String dateRange) {
        try {
            switch (fieldId) {
                case "gender":
                    return matchStr(customer.getGender(), operator, value);
                case "membershipLevel":
                    return matchStr(customer.getMembershipLevel(), operator, value);
                case "preferredStyle":
                    return matchStr(customer.getPreferredStyle(), operator, value);
                case "activityLevel":
                    return matchStr(customer.getActivityLevel(), operator, value);
                case "coldSensitivity":
                    return matchNum(customer.getColdSensitivity() != null
                            ? customer.getColdSensitivity().doubleValue() : 0, operator, Double.parseDouble(value));
                case "age":
                    if (customer.getBirthDate() == null) return false;
                    int age = java.time.Period.between(customer.getBirthDate(), LocalDate.now()).getYears();
                    return matchNum(age, operator, Double.parseDouble(value));
                case "purchaseCount": {
                    double count;
                    if ("campaign".equals(dateRange) && campaign.getStartDate() != null) {
                        count = purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(
                                customer.getId(), "PURCHASED", campaign.getStartDate().atStartOfDay()).size();
                    } else {
                        count = purchaseRepository.findByCustomerIdAndStatus(customer.getId(), "PURCHASED").size();
                    }
                    return matchNum(count, operator, Double.parseDouble(value));
                }
                case "totalPurchaseAmount": {
                    double total;
                    if ("campaign".equals(dateRange) && campaign.getStartDate() != null) {
                        total = purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(
                                customer.getId(), "PURCHASED", campaign.getStartDate().atStartOfDay())
                                .stream().mapToDouble(p -> p.getPrice() != null ? p.getPrice() : 0).sum();
                    } else {
                        Integer sum = purchaseRepository.sumPurchasedAmountByCustomerId(customer.getId());
                        total = sum != null ? sum.doubleValue() : 0.0;
                    }
                    return matchNum(total, operator, Double.parseDouble(value));
                }
                case "cartCount": {
                    double count;
                    if ("campaign".equals(dateRange) && campaign.getStartDate() != null) {
                        count = purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(
                                customer.getId(), "CART", campaign.getStartDate().atStartOfDay()).size();
                    } else {
                        count = purchaseRepository.findByCustomerIdAndStatus(customer.getId(), "CART").size();
                    }
                    return matchNum(count, operator, Double.parseDouble(value));
                }
                case "wishlistCount": {
                    double count;
                    if ("campaign".equals(dateRange) && campaign.getStartDate() != null) {
                        count = purchaseRepository.findByCustomerIdAndStatusAndPurchasedAtAfter(
                                customer.getId(), "WISHLIST", campaign.getStartDate().atStartOfDay()).size();
                    } else {
                        count = purchaseRepository.findByCustomerIdAndStatus(customer.getId(), "WISHLIST").size();
                    }
                    return matchNum(count, operator, Double.parseDouble(value));
                }
                default:
                    // 알 수 없는 필드 → 조건 불일치로 처리
                    return false;
            }
        } catch (Exception e) {
            // 개별 필드 평가 실패 시 안전하게 미표시 처리
            return false;
        }
    }

    private boolean matchStr(String actual, String operator, String value) {
        if (actual == null) return false;
        switch (operator) {
            case "=":    return actual.equals(value);
            case "!=":   return !actual.equals(value);
            case "LIKE": return actual.contains(value);
            case "IN":   return List.of(value.split(",")).contains(actual);
            default:     return true;
        }
    }

    private boolean matchNum(double actual, String operator, double value) {
        switch (operator) {
            case ">":  return actual >  value;
            case ">=": return actual >= value;
            case "<":  return actual <  value;
            case "<=": return actual <= value;
            case "=":  return actual == value;
            case "!=": return actual != value;
            default:   return true;
        }
    }
}