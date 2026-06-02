package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousPopupRule;
import com.weatherfit.weatherfitBackend.domain.entity.Coupon;
import com.weatherfit.weatherfitBackend.domain.repository.AnonymousPopupRuleRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anonymous-popup-rules")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AnonymousPopupRuleController {

    private final AnonymousPopupRuleRepository ruleRepository;
    private final CouponRepository couponRepository;

    @GetMapping
    public ResponseEntity<List<AnonymousPopupRule>> getAll() {
        List<AnonymousPopupRule> rules = ruleRepository.findAll();

        Map<Long, String> couponNameMap = rules.stream()
            .filter(r -> r.getCouponId() != null)
            .map(AnonymousPopupRule::getCouponId)
            .distinct()
            .collect(Collectors.toMap(
                id -> id,
                id -> couponRepository.findById(id).map(Coupon::getCouponName).orElse(null),
                (a, b) -> a
            ));

        rules.forEach(r -> {
            if (r.getCouponId() != null) {
                r.setCouponName(couponNameMap.get(r.getCouponId()));
            }
        });

        return ResponseEntity.ok(rules);
    }

    @PostMapping
    public ResponseEntity<AnonymousPopupRule> create(@RequestBody Map<String, Object> body) {
        AnonymousPopupRule rule = AnonymousPopupRule.builder()
            .ruleType(getString(body, "ruleType"))
            .threshold(getInt(body, "threshold"))
            .message(getString(body, "message"))
            .couponId(getLong(body, "couponId"))
            .isActive(body.containsKey("isActive") ? Boolean.parseBoolean(body.get("isActive").toString()) : true)
            .build();
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnonymousPopupRule> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ruleRepository.findById(id).map(rule -> {
            if (body.containsKey("ruleType")) rule.setRuleType(getString(body, "ruleType"));
            if (body.containsKey("threshold")) rule.setThreshold(getInt(body, "threshold"));
            if (body.containsKey("message")) rule.setMessage(getString(body, "message"));
            rule.setCouponId(getLong(body, "couponId"));
            if (body.containsKey("isActive")) rule.setIsActive(Boolean.parseBoolean(body.get("isActive").toString()));
            return ResponseEntity.ok(ruleRepository.save(rule));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<AnonymousPopupRule> toggle(@PathVariable Long id) {
        return ruleRepository.findById(id).map(rule -> {
            rule.setIsActive(!Boolean.TRUE.equals(rule.getIsActive()));
            return ResponseEntity.ok(ruleRepository.save(rule));
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer getInt(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) return null;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return null; }
    }

    private Long getLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null || val.toString().isBlank()) return null;
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }
}
