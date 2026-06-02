package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.Coupon;
import com.weatherfit.weatherfitBackend.domain.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CouponController {

    private final CouponRepository couponRepository;

    @GetMapping
    public List<Coupon> getAll() {
        return couponRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getById(@PathVariable Long id) {
        return couponRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Coupon create(@RequestBody Coupon coupon) {
        Coupon newCoupon = Coupon.builder()
                .couponName(coupon.getCouponName())
                .code(coupon.getCode())
                .type(coupon.getType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .validDays(coupon.getValidDays())
                .targetCategory(coupon.getTargetCategory())
                .campaignId(coupon.getCampaignId())
                .issuedCount(coupon.getIssuedCount())
                .usedCount(coupon.getUsedCount())
                .status(coupon.getStatus())
                .expiredAt(coupon.getExpiredAt())
                .description(coupon.getDescription())
                .build();
        return couponRepository.save(newCoupon);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> update(@PathVariable Long id, @RequestBody Coupon coupon) {
        if (!couponRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(couponRepository.save(coupon));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!couponRepository.existsById(id)) return ResponseEntity.notFound().build();
        couponRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
