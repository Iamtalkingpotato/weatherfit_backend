package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.BehaviorLog;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.repository.BehaviorLogRepository;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import com.weatherfit.weatherfitBackend.dto.BehaviorConversionDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorDetailDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorHourlyDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorPopularPageDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorPopularProductDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorSummaryDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorTypeCountDto;
import com.weatherfit.weatherfitBackend.dto.BehaviorWishlistConversionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/behavior-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BehaviorLogController {

    private final BehaviorLogRepository behaviorLogRepository;
    private final CustomerRepository    customerRepository;

    /**
     * days=0  → null (전체)
     * days=1  → 오늘 00:00:00 (당일 데이터)
     * days=N  → N일 전 00:00:00 ~ 현재
     */
    private LocalDateTime sinceDate(int days) {
        if (days <= 0) return null;
        if (days == 1) return LocalDate.now().atStartOfDay();          // 오늘 00:00
        return LocalDate.now().minusDays(days).atStartOfDay();         // N일 전 00:00
    }

    @GetMapping
    public List<BehaviorLog> getAll() {
        return behaviorLogRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BehaviorLog> getById(@PathVariable Long id) {
        return behaviorLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<BehaviorLog> getByCustomer(@PathVariable Long customerId) {
        return behaviorLogRepository.findByCustomerId(customerId);
    }

    @PostMapping
    public BehaviorLog create(@RequestBody BehaviorLog log) {
        return behaviorLogRepository.save(log);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BehaviorLog> update(@PathVariable Long id, @RequestBody BehaviorLog log) {
        if (!behaviorLogRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(behaviorLogRepository.save(log));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!behaviorLogRepository.existsById(id)) return ResponseEntity.notFound().build();
        behaviorLogRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── 1. 요약 지표 ──────────────────────────────────────────────────────────
    @GetMapping("/summary")
    public BehaviorSummaryDto getSummary(
            @RequestParam(required = false, defaultValue = "0") int days) {
        LocalDateTime since = sinceDate(days);
        long   totalViews     = behaviorLogRepository.countViews(since);
        Double rawDuration    = behaviorLogRepository.avgDuration(since);
        Double rawScrollDepth = behaviorLogRepository.avgScrollDepth(since);

        double avgDuration    = rawDuration    == null ? 0.0 : Math.round(rawDuration    * 10.0) / 10.0;
        double avgScrollDepth = rawScrollDepth == null ? 0.0 : Math.round(rawScrollDepth * 10.0) / 10.0;

        return new BehaviorSummaryDto(totalViews, avgDuration, avgScrollDepth);
    }

    // ── 2. 이벤트 타입별 건수 ──────────────────────────────────────────────────
    @GetMapping("/type-counts")
    public List<BehaviorTypeCountDto> getTypeCounts(
            @RequestParam(required = false, defaultValue = "0") int days) {
        return behaviorLogRepository.countByEventType(sinceDate(days)).stream()
                .map(row -> new BehaviorTypeCountDto(
                        (String) row[0],
                        ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    // ── 3. 시간대별 건수 ──────────────────────────────────────────────────────
    @GetMapping("/hourly")
    public List<BehaviorHourlyDto> getHourlyCounts(
            @RequestParam(required = false, defaultValue = "0") int days) {
        return behaviorLogRepository.countByHour(sinceDate(days)).stream()
                .map(row -> new BehaviorHourlyDto(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    // ── 5. 전환율 ─────────────────────────────────────────────────────────────
    @GetMapping("/conversion-rate")
    public BehaviorConversionDto getConversionRate(
            @RequestParam(required = false, defaultValue = "0") int days) {
        LocalDateTime since = sinceDate(days);
        long   sessionCount     = behaviorLogRepository.countViews(since);
        Double rawDuration      = behaviorLogRepository.avgDuration(since);
        Double rawScrollDepth   = behaviorLogRepository.avgScrollDepth(since);
        long   purchaseCount    = behaviorLogRepository.countPurchases(since);
        long   productViewCount = behaviorLogRepository.countProductViews(since);

        double avgDuration            = rawDuration    == null ? 0.0 : Math.round(rawDuration    * 10.0) / 10.0;
        double avgScrollDepth         = rawScrollDepth == null ? 0.0 : Math.round(rawScrollDepth * 10.0) / 10.0;
        double purchaseConversionRate = productViewCount == 0  ? 0.0
                : Math.round((double) purchaseCount / productViewCount * 100 * 10.0) / 10.0;

        return new BehaviorConversionDto(sessionCount, avgDuration, avgScrollDepth, purchaseConversionRate);
    }

    // ── 6. 인기 상품 TOP 5 ────────────────────────────────────────────────────
    @GetMapping("/popular-products")
    public List<BehaviorPopularProductDto> getPopularProducts(
            @RequestParam(required = false, defaultValue = "0") int days) {
        return behaviorLogRepository.findTopProductsByView(sinceDate(days)).stream()
                .map(row -> new BehaviorPopularProductDto(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    // ── 7. 인기 페이지 TOP 5 ──────────────────────────────────────────────────
    @GetMapping("/popular-pages")
    public List<BehaviorPopularPageDto> getPopularPages(
            @RequestParam(required = false, defaultValue = "0") int days) {
        return behaviorLogRepository.findTopPagesByView(sinceDate(days)).stream()
                .map(row -> new BehaviorPopularPageDto(
                        (String) row[0],
                        ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    // ── 8. 위시리스트 전환율 ──────────────────────────────────────────────────
    @GetMapping("/wishlist-conversion")
    public BehaviorWishlistConversionDto getWishlistConversion(
            @RequestParam(required = false, defaultValue = "0") int days) {
        LocalDateTime since = sinceDate(days);
        long wishlistCount = behaviorLogRepository.countWishlists(since);
        long purchaseCount = behaviorLogRepository.countWishlistConversions(since);
        double conversionRate = wishlistCount == 0 ? 0.0
                : Math.round((double) purchaseCount / wishlistCount * 100 * 10.0) / 10.0;

        return new BehaviorWishlistConversionDto(wishlistCount, purchaseCount, conversionRate);
    }

    // ── 4. 상세 목록 (type · hour · days 필터) ──────────────────────────────
    @GetMapping("/detail")
    public List<BehaviorDetailDto> getDetail(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false, defaultValue = "0") int days) {

        LocalDateTime since = days > 0 ? LocalDateTime.now().minusDays(days) : null;

        List<BehaviorLog> logs = (hour != null)
                ? behaviorLogRepository.findByEventTypeAndHourOptional(type, hour, since)
                : behaviorLogRepository.findByEventTypeOptional(type, since);

        Set<Long> customerIds = logs.stream()
                .map(BehaviorLog::getCustomerId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));

        // 날짜+시각 전체 포맷 (yyyy.MM.dd HH:mm)
        DateTimeFormatter fmt    = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        DateTimeFormatter dedupFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // wishlist/purchase 계열은 pageUrl 없어도 포함
        Set<String> noPageTypes = Set.of(
                "wishlist_add", "wishlist_remove", "WISHLIST",
                "purchase", "PURCHASE", "add_to_cart", "CART");
        boolean requirePage = type == null || !noPageTypes.contains(type);

        return logs.stream()
                .filter(b -> !requirePage || (b.getPageUrl() != null && !b.getPageUrl().isBlank()))
                .collect(Collectors.toMap(
                        b -> String.join("|",
                                String.valueOf(b.getCustomerId()),
                                String.valueOf(b.getEventType()),
                                String.valueOf(b.getItemId()),
                                b.getPageUrl() != null ? b.getPageUrl() : "",
                                b.getCreatedAt() != null ? b.getCreatedAt().format(dedupFmt) : ""),
                        b -> b,
                        (a, b) -> a.getId() != null && b.getId() != null && a.getId() <= b.getId() ? a : b,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                // 최신순 정렬
                .sorted(Comparator.comparing(
                        (BehaviorLog b) -> b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN,
                        Comparator.reverseOrder()))
                // 최대 50건
                .limit(50)
                .map(b -> new BehaviorDetailDto(
                        b.getId(),
                        b.getCustomerId() != null ? nameMap.getOrDefault(b.getCustomerId(), "-") : "-",
                        b.getEventType(),
                        b.getPageUrl(),
                        b.getDuration(),
                        b.getScrollDepth(),
                        b.getCreatedAt() != null ? b.getCreatedAt().format(fmt) : null))
                .collect(Collectors.toList());
    }
}
