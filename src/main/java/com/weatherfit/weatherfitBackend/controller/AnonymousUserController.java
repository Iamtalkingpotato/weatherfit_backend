package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousUser;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private final AnonymousUserRepository anonymousUserRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignActionRepository campaignActionRepository;
    private final CustomerCouponRepository customerCouponRepository;


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
            user.setPopupShown(true);
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

    // 회원가입 전환 업데이트
    @PatchMapping("/{anonymousId}/converted")
    public ResponseEntity<Void> markConverted(@PathVariable String anonymousId) {
        anonymousUserRepository.findByAnonymousId(anonymousId).ifPresent(user -> {
            user.setConverted(true);
            anonymousUserRepository.save(user);
        });
        return ResponseEntity.ok().build();
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
        user.setPopupShown(true);
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