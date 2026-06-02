package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousUser;
import com.weatherfit.weatherfitBackend.domain.entity.Campaign;
import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousUserRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignActionRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignPopupController {

    private final AnonymousUserRepository  anonymousUserRepository;
    private final CampaignRepository       campaignRepository;
    private final CampaignActionRepository campaignActionRepository;
    private final CustomerRepository       customerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 비로그인 팝업 체크 ─────────────────────────────────────
    // GET /api/campaigns/popup-check?anonymousId=550e8400-xxxx
    @GetMapping("/popup-check")
    public ResponseEntity<Map<String, Object>> popupCheck(
            @RequestParam String anonymousId) {

        Map<String, Object> response = new HashMap<>();

        // 1. 해당 UUID 사용자 조회
        Optional<AnonymousUser> userOpt = anonymousUserRepository.findByAnonymousId(anonymousId);
        if (userOpt.isEmpty()) {
            response.put("showPopup", false);
            return ResponseEntity.ok(response);
        }

        AnonymousUser user = userOpt.get();

        // 2. 이미 팝업 노출됐거나 전환된 경우 스킵
        if (Boolean.TRUE.equals(user.getPopupShown()) || Boolean.TRUE.equals(user.getConverted())) {
            response.put("showPopup", false);
            return ResponseEntity.ok(response);
        }

        // 3. 활성 캠페인 중 anonymousMinVisits 조건 + POPUP 액션 있는 것 탐색
        LocalDate today = LocalDate.now();
        Optional<Campaign> campaignOpt = campaignRepository.findAll().stream()
                .filter(c -> "실행중".equals(c.getStatus()))
                .filter(c -> c.getAnonymousMinVisits() != null)
                .filter(c -> c.getStartDate() != null && !today.isBefore(c.getStartDate()))
                .filter(c -> c.getEndDate() != null && !today.isAfter(c.getEndDate()))
                .filter(c -> user.getVisitCount() >= c.getAnonymousMinVisits())
                .findFirst();

        if (campaignOpt.isPresent()) {
            Campaign campaign = campaignOpt.get();
            Optional<CampaignAction> popupAction = campaignActionRepository
                    .findByCampaignId(campaign.getId()).stream()
                    .filter(a -> "POPUP".equals(a.getActionType()))
                    .findFirst();

            if (popupAction.isPresent()) {
                response.put("showPopup", true);
                response.put("campaignId", campaign.getId());
                response.put("message", popupAction.get().getPopupMessage() != null
                        ? popupAction.get().getPopupMessage()
                        : "자주 방문하시네요! 회원가입하고 더 많은 혜택을 받아보세요.");
            } else {
                response.put("showPopup", false);
            }
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

        // 2. 진행중인 캠페인 중 POPUP 액션 있는 것 탐색
        LocalDate today = LocalDate.now();
        Optional<Campaign> campaignOpt = campaignRepository.findAll().stream()
                .filter(c -> "실행중".equals(c.getStatus()))
                .filter(c -> c.getStartDate() != null && !today.isBefore(c.getStartDate()))
                .filter(c -> c.getEndDate() != null && !today.isAfter(c.getEndDate()))
                .filter(c -> matchesFilter(c, customer))
                .findFirst();

        if (campaignOpt.isPresent()) {
            Campaign campaign = campaignOpt.get();
            Optional<CampaignAction> popupAction = campaignActionRepository
                    .findByCampaignId(campaign.getId()).stream()
                    .filter(a -> "POPUP".equals(a.getActionType()))
                    .findFirst();

            if (popupAction.isPresent()) {
                response.put("showPopup", true);
                response.put("campaignId", campaign.getId());
                response.put("message", popupAction.get().getPopupMessage() != null
                        ? popupAction.get().getPopupMessage()
                        : "특별 혜택을 확인하세요!");
            } else {
                response.put("showPopup", false);
            }
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
                String fieldId  = String.valueOf(cond.get("fieldId"));
                String operator = String.valueOf(cond.get("operator"));
                String value    = String.valueOf(cond.get("value"));
                if (!matchField(customer, fieldId, operator, value)) return false;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean matchField(Customer customer, String fieldId, String operator, String value) {
        try {
            switch (fieldId) {
                case "gender":
                    return matchStr(customer.getGender(), operator, value);
                case "membershipLevel":
                    return matchStr(customer.getMembershipLevel(), operator, value);
                case "preferredStyle":
                    return matchStr(customer.getPreferredStyle(), operator, value);
                case "coldSensitivity":
                    return matchNum(customer.getColdSensitivity() != null
                            ? customer.getColdSensitivity().doubleValue() : 0, operator, Double.parseDouble(value));
                case "age":
                    if (customer.getBirthDate() == null) return false;
                    int age = java.time.Period.between(customer.getBirthDate(), LocalDate.now()).getYears();
                    return matchNum(age, operator, Double.parseDouble(value));
                default:
                    return true;
            }
        } catch (Exception e) {
            return true;
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