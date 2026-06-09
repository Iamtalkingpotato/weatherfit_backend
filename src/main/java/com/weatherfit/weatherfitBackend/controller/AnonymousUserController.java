package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousUser;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousPopupRule;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousPopupRuleRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CampaignActionRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerCouponRepository;
import com.weatherfit.weatherfitBackend.domain.entity.CustomerCoupon;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/anonymous-users")
@RequiredArgsConstructor
public class AnonymousUserController {

    private final AnonymousUserRepository      anonymousUserRepository;
    private final AnonymousPopupRuleRepository anonymousPopupRuleRepository;
    private final CampaignRepository           campaignRepository;
    private final CampaignActionRepository     campaignActionRepository;
    private final CustomerCouponRepository     customerCouponRepository;


    // 목록 조회 (최근 방문순)
    @GetMapping
    public ResponseEntity<List<AnonymousUser>> getAll() {
        List<AnonymousUser> list = anonymousUserRepository.findAll(
            Sort.by(Sort.Direction.DESC, "lastVisit")
        );
        return ResponseEntity.ok(list);
    }

    // 상세 조회
    @GetMapping("/{anonymousId}")
    public ResponseEntity<AnonymousUser> getOne(@PathVariable String anonymousId) {
        return anonymousUserRepository.findByAnonymousId(anonymousId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 팝업 노출 업데이트 (파트너가 팝업 띄운 후 호출)
    @PatchMapping("/{anonymousId}/popup-shown")
    public ResponseEntity<Void> markPopupShown(@PathVariable String anonymousId) {
        anonymousUserRepository.findByAnonymousId(anonymousId).ifPresent(user -> {
            user.setPopupShown(user.getPopupShown() != null ? user.getPopupShown() + 1 : 1);
            anonymousUserRepository.save(user);
        });
        return ResponseEntity.ok().build();
    }

    // UUID 삭제
    @DeleteMapping("/{anonymousId}")
    public ResponseEntity<Void> deleteAnonymousUser(@PathVariable String anonymousId) {
        anonymousUserRepository.findByAnonymousId(anonymousId).ifPresent(user -> {
            anonymousUserRepository.delete(user);
        });
        return ResponseEntity.ok().build();
    }

    // 회원 전환 처리 + 쿠폰 자동 발급 (신규 가입자만)
    // PATCH /api/anonymous-users/{anonymousId}/converted
    // body: { customerId: Long, isNewUser: boolean }
    @PatchMapping("/{anonymousId}/converted")
    public ResponseEntity<Map<String, Object>> markConverted(
            @PathVariable String anonymousId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();

        // 1. anonymous_user 조회
        AnonymousUser user = anonymousUserRepository.findByAnonymousId(anonymousId).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "익명 사용자를 찾을 수 없습니다.");
            return ResponseEntity.status(404).body(response);
        }

        // 2. converted = true 업데이트
        user.setConverted(true);
        anonymousUserRepository.save(user);

        // 3. isNewUser=false(기존 회원 로그인)이면 쿠폰 발급 없이 종료
        boolean isNewUser = Boolean.TRUE.equals(body.get("isNewUser"));
        if (!isNewUser) {
            response.put("success", true);
            response.put("couponIssued", false);
            return ResponseEntity.ok(response);
        }

        // 4. customerId 파싱
        Object customerIdRaw = body.get("customerId");
        if (customerIdRaw == null) {
            response.put("success", true);
            response.put("couponIssued", false);
            return ResponseEntity.ok(response);
        }
        Long customerId;
        try {
            customerId = Long.valueOf(customerIdRaw.toString());
        } catch (NumberFormatException e) {
            response.put("success", false);
            response.put("message", "유효하지 않은 customerId입니다.");
            return ResponseEntity.badRequest().body(response);
        }

        // 5. 이 익명 유저가 팝업을 본 적 있는 경우에만 쿠폰 발급 검토
        //    활성 팝업 규칙 중 해당 유저의 방문 조건에 맞고 coupon_id가 있는 첫 번째 규칙 사용
        final AnonymousUser finalUser = user;
        Long couponIdToIssue = anonymousPopupRuleRepository.findByIsActiveTrue().stream()
                .filter(r -> r.getCouponId() != null)
                .filter(r -> {
                    if ("visit_count".equals(r.getRuleType())) {
                        return finalUser.getVisitCount() != null
                                && finalUser.getVisitCount() >= r.getThreshold();
                    } else if ("repeat_interval".equals(r.getRuleType())) {
                        return finalUser.getVisitCount() != null
                                && r.getThreshold() > 0
                                && finalUser.getVisitCount() % r.getThreshold() == 0;
                    }
                    return false;
                })
                .map(AnonymousPopupRule::getCouponId)
                .findFirst()
                .orElse(null);

        if (couponIdToIssue == null) {
            response.put("success", true);
            response.put("couponIssued", false);
            return ResponseEntity.ok(response);
        }

        // 6. 중복 발급 방지
        if (customerCouponRepository.existsByCustomerIdAndCouponId(customerId, couponIdToIssue)) {
            response.put("success", true);
            response.put("couponIssued", false);
            response.put("message", "이미 발급된 쿠폰입니다.");
            return ResponseEntity.ok(response);
        }

        // 7. customer_coupon INSERT
        customerCouponRepository.save(CustomerCoupon.builder()
                .customerId(customerId)
                .couponId(couponIdToIssue)
                .status("ISSUED")
                .build());

        response.put("success", true);
        response.put("couponIssued", true);
        response.put("couponId", couponIdToIssue);
        return ResponseEntity.ok(response);
    }

    // 회원가입 연동 API
@PostMapping("/{anonymousId}/link")
public ResponseEntity<Map<String, Object>> linkToCustomer(
        @PathVariable String anonymousId,
        @RequestBody Map<String, Object> body) {

    Map<String, Object> response = new HashMap<>();
    Long customerId = Long.valueOf(body.get("customerId").toString());

    // 1. wardrobe_item, temperature_feedback anonymous_id → customer_id 업데이트
    // (Native Query 사용)
    anonymousUserRepository.findByAnonymousId(anonymousId).ifPresent(user -> {
        user.setConverted(true);
        user.setPopupShown(user.getPopupShown() != null ? user.getPopupShown() + 1 : 1);
        anonymousUserRepository.save(user);
    });

    anonymousUserRepository.linkWardrobeItems(customerId, anonymousId);
    anonymousUserRepository.linkFeedbacks(customerId, anonymousId);
    anonymousUserRepository.linkPurchases(customerId, anonymousId);

    // 2. 캠페인 쿠폰 발급
    java.time.LocalDate today = java.time.LocalDate.now();
    campaignRepository.findAll().stream()
            .filter(c -> "실행중".equals(c.getStatus()))
            .filter(c -> c.getAnonymousMinVisits() != null)
            .filter(c -> c.getStartDate() != null && !today.isBefore(c.getStartDate()))
            .filter(c -> c.getEndDate() != null && !today.isAfter(c.getEndDate()))
            .findFirst()
            .ifPresent(campaign -> {
                campaignActionRepository.findByCampaignId(campaign.getId()).stream()
                        .filter(a -> "COUPON".equals(a.getActionType()))
                        .filter(a -> a.getCouponId() != null)
                        .forEach(a -> {
                            CustomerCoupon cc = CustomerCoupon.builder()
                                    .customerId(customerId)
                                    .couponId(a.getCouponId())
                                    .issuedAt(LocalDateTime.now())
                                    .expiredAt(LocalDateTime.now().plusDays(30))
                                    .status("ISSUED")
                                    .build();
                            customerCouponRepository.save(cc);
                        });
                response.put("campaignId", campaign.getId());
            });

    response.put("success", true);
    response.put("customerId", customerId);
    return ResponseEntity.ok(response);
}
}