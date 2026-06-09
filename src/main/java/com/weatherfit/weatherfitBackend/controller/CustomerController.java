package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.entity.Product;
import com.weatherfit.weatherfitBackend.dto.CustomerDto;
import com.weatherfit.weatherfitBackend.domain.entity.Purchase;
import com.weatherfit.weatherfitBackend.domain.entity.WardrobeItem;
import com.weatherfit.weatherfitBackend.domain.repository.BehaviorLogRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerCouponRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.weatherfit.weatherfitBackend.domain.repository.ProductRepository;
import com.weatherfit.weatherfitBackend.domain.repository.PurchaseRepository;
import com.weatherfit.weatherfitBackend.domain.repository.TemperatureFeedbackRepository;
import com.weatherfit.weatherfitBackend.domain.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Transactional
public class CustomerController {

    private final CustomerRepository            customerRepository;
    private final ProductRepository             productRepository;
    private final PurchaseRepository            purchaseRepository;
    private final TemperatureFeedbackRepository feedbackRepository;
    private final WardrobeItemRepository        wardrobeItemRepository;
    private final CustomerCouponRepository      customerCouponRepository;
    private final BehaviorLogRepository         behaviorLogRepository;

    @GetMapping
    public List<CustomerDto> getAll() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.withDayOfMonth(1).minusMonths(3).atStartOfDay();
        LocalDateTime end   = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);

        Map<Long, Integer> periodTotals = toIntMap(
                purchaseRepository.sumPurchasedAmountByCustomerBetween(start, end));
        Map<Long, Integer> totalPurchaseAmounts = toIntMap(
                purchaseRepository.sumPurchasedAmountByCustomer());
        Map<Long, Integer> feedbackCounts = toIntMap(feedbackRepository.countByCustomer());

        return customerRepository.findAll().stream()
                .map(customer -> {
                    Long cid = customer.getId();

                    int periodTotal = periodTotals.getOrDefault(cid, 0);

                    String nextLevel;
                    if      (periodTotal >= 500000) nextLevel = "VIP";
                    else if (periodTotal >= 300000) nextLevel = "GOLD";
                    else if (periodTotal >= 100000) nextLevel = "SILVER";
                    else                            nextLevel = "BASIC";

                    int totalPurchaseAmount = totalPurchaseAmounts.getOrDefault(cid, 0);
                    int totalFeedbackCount = feedbackCounts.getOrDefault(cid, 0);

                    return CustomerDto.from(customer, nextLevel, totalPurchaseAmount, totalFeedbackCount);
                })
                .collect(Collectors.toList());
    }

    private Map<Long, Integer> toIntMap(List<Object[]> rows) {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            Long customerId = ((Number) row[0]).longValue();
            Integer value = row[1] == null ? 0 : ((Number) row[1]).intValue();
            result.put(customerId, value);
        }
        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Customer create(@RequestBody Customer customer) {
        return customerRepository.save(customer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Customer customer) {
        if (!customerRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(customerRepository.save(customer));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        Optional<Customer> optional = customerRepository.findById(id);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();

        Customer customer = optional.get();

        if (data.containsKey("cold_sensitivity")) {
            customer.setColdSensitivity(Integer.parseInt(String.valueOf(data.get("cold_sensitivity"))));
        }
        if (data.containsKey("preferred_style")) {
            customer.setPreferredStyle(String.valueOf(data.get("preferred_style")).toUpperCase());
        }
        if (data.containsKey("activity_level")) {
            customer.setActivityLevel(String.valueOf(data.get("activity_level")).toUpperCase());
        }
        if (data.containsKey("marketing_consent")) {
            customer.setMarketingConsent(Boolean.parseBoolean(String.valueOf(data.get("marketing_consent"))));
        }
        if (data.containsKey("push_consent")) {
            customer.setPushConsent(Boolean.parseBoolean(String.valueOf(data.get("push_consent"))));
        }
        if (data.containsKey("email_consent")) {
            customer.setEmailConsent(Boolean.parseBoolean(String.valueOf(data.get("email_consent"))));
        }
        if (data.containsKey("sms_consent")) {
            customer.setSmsConsent(Boolean.parseBoolean(String.valueOf(data.get("sms_consent"))));
        }

        customerRepository.save(customer);

        Map<String, Object> result = new HashMap<>();
        result.put("customer_id", id);
        result.put("message", "업데이트 성공");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!customerRepository.existsById(id)) return ResponseEntity.notFound().build();
        behaviorLogRepository.deleteAll(behaviorLogRepository.findByCustomerId(id));
        feedbackRepository.deleteAll(feedbackRepository.findByCustomerId(id));
        wardrobeItemRepository.deleteAll(wardrobeItemRepository.findByCustomerId(id));
        purchaseRepository.deleteAll(purchaseRepository.findByCustomerId(id));
        customerCouponRepository.deleteAll(customerCouponRepository.findByCustomerId(id));
        customerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(@PathVariable Long id) {
        if (!customerRepository.existsById(id)) return ResponseEntity.notFound().build();

        LocalDateTime since = LocalDateTime.now().minusYears(1);

        List<String> statuses = List.of("PURCHASED", "WISHLIST", "CART");
        List<Purchase> purchases = purchaseRepository.findRecentByCustomerIdAndStatuses(id, statuses, since);
        Set<Long> productIds = purchases.stream()
                .map(Purchase::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Product> products = productRepository.findAllById(productIds);

        List<WardrobeItem> wardrobeItems = wardrobeItemRepository.findRecentByCustomerId(id, since);

        Map<String, Long> styleCount    = new HashMap<>();
        Map<Long, Long>   colorCount    = new HashMap<>();
        Map<String, Long> categoryCount = new HashMap<>();

        for (Product p : products) {
            if (p.getStyle()    != null) styleCount.merge(p.getStyle(), 1L, Long::sum);
            if (p.getColorId()  != null) colorCount.merge(p.getColorId(), 1L, Long::sum);
            if (p.getCategory() != null) categoryCount.merge(p.getCategory(), 1L, Long::sum);
        }
        for (WardrobeItem w : wardrobeItems) {
            if (w.getStyle()    != null) styleCount.merge(w.getStyle(), 1L, Long::sum);
            if (w.getColorId()  != null) colorCount.merge(w.getColorId(), 1L, Long::sum);
            if (w.getCategory() != null) categoryCount.merge(w.getCategory(), 1L, Long::sum);
        }

        List<String> topStyles = styleCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).collect(Collectors.toList());

        List<Long> topColors = colorCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).collect(Collectors.toList());

        List<String> topCategories = categoryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("top_styles",     topStyles);
        result.put("top_colors",     topColors);
        result.put("top_categories", topCategories);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody Map<String, Object> data) {
        try {
            String email = String.valueOf(data.get("email"));
            if (customerRepository.existsByEmail(email)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "이미 사용중인 이메일입니다.");
                return ResponseEntity.badRequest().body(error);
            }
            Object rawPassword = data.get("password");
            if (rawPassword == null || String.valueOf(rawPassword).isBlank()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "비밀번호는 필수입니다.");
                return ResponseEntity.badRequest().body(error);
            }
            // 비밀번호는 클라이언트 또는 별도 서비스에서 해시 처리 후 전달받거나,
            // 여기서 BCryptPasswordEncoder 등으로 해싱하여 저장하세요.
            String passwordHash = String.valueOf(rawPassword);

            Customer customer = customerRepository.save(Customer.builder()
                    .name(String.valueOf(data.get("name")))
                    .email(email)
                    .passwordHash(passwordHash)
                    .phone(String.valueOf(data.get("phone")))
                    .gender(String.valueOf(data.get("gender")))
                    .birthDate(LocalDate.parse(String.valueOf(data.get("birth_date"))))
                    .joinDate(LocalDate.now())
                    .preferredStyle(String.valueOf(data.getOrDefault("preferred_style", "CASUAL")).toUpperCase())
                    .activityLevel(String.valueOf(data.getOrDefault("activity_level", "MEDIUM")).toUpperCase())
                    .coldSensitivity(data.get("cold_sensitivity") != null ? Integer.parseInt(String.valueOf(data.get("cold_sensitivity"))) : 3)
                    .marketingConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("marketing_consent", false))))
                    .pushConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("push_consent", false))))
                    .emailConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("email_consent", false))))
                    .smsConsent(Boolean.parseBoolean(String.valueOf(data.getOrDefault("sms_consent", false))))
                    .build());

            Map<String, Object> result = new HashMap<>();
            result.put("customer_id", customer.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
