package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.dto.BehaviorTypeCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.weatherfit.weatherfitBackend.domain.entity.BehaviorLog;

import java.time.LocalDateTime;
import java.util.List;

public interface BehaviorLogRepository extends JpaRepository<BehaviorLog, Long> {

    List<BehaviorLog> findByCustomerId(Long customerId);

    // ── 1. 요약 (summary) ─────────────────────────────────────────────────────
    @Query("SELECT COUNT(b) FROM BehaviorLog b WHERE b.eventType IN ('page_view', 'VIEW') " +
           "AND (:since IS NULL OR b.createdAt >= :since) " +
           "AND (:until IS NULL OR b.createdAt < :until)")
    long countViews(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("SELECT AVG(b.duration) FROM BehaviorLog b WHERE b.duration IS NOT NULL " +
           "AND (:since IS NULL OR b.createdAt >= :since) " +
           "AND (:until IS NULL OR b.createdAt < :until)")
    Double avgDuration(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("SELECT AVG(b.scrollDepth) FROM BehaviorLog b WHERE b.scrollDepth IS NOT NULL " +
           "AND (:since IS NULL OR b.createdAt >= :since) " +
           "AND (:until IS NULL OR b.createdAt < :until)")
    Double avgScrollDepth(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    // ── 2. 이벤트 타입별 건수 ──────────────────────────────────────────────────
    @Query(value = "SELECT event_type, COUNT(DISTINCT CONCAT(" +
                   "COALESCE(customer_id, -1), '|', COALESCE(item_id, -1), '|', " +
                   "COALESCE(page_url, ''), '|', DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')" +
                   ")) AS cnt " +
                   "FROM behavior_log " +
                   "WHERE event_type IS NOT NULL " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until) " +
                   "GROUP BY event_type ORDER BY cnt DESC",
           nativeQuery = true)
    List<Object[]> countByEventType(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    // ── 3. 시간대별 건수 ──────────────────────────────────────────────────────
    @Query(value = "SELECT HOUR(created_at) AS hour, COUNT(*) AS cnt " +
                   "FROM behavior_log " +
                   "WHERE created_at IS NOT NULL " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until) " +
                   "GROUP BY HOUR(created_at) " +
                   "ORDER BY HOUR(created_at)",
           nativeQuery = true)
    List<Object[]> countByHour(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    // ── 4. 상세 목록 ──────────────────────────────────────────────────────────
    @Query("SELECT b FROM BehaviorLog b " +
           "WHERE (:type IS NULL OR b.eventType = :type) " +
           "AND (:since IS NULL OR b.createdAt >= :since) " +
           "AND (:until IS NULL OR b.createdAt < :until) " +
           "ORDER BY b.createdAt DESC")
    List<BehaviorLog> findByEventTypeOptional(
            @Param("type") String type,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until);

    @Query(value = "SELECT * FROM behavior_log " +
                   "WHERE (:type IS NULL OR event_type = :type) " +
                   "AND (:hour IS NULL OR HOUR(created_at) = :hour) " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until) " +
                   "ORDER BY created_at DESC",
           nativeQuery = true)
    List<BehaviorLog> findByEventTypeAndHourOptional(
            @Param("type") String type,
            @Param("hour") Integer hour,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until);

    // ── 5. 전환율 ─────────────────────────────────────────────────────────────
    @Query(value = "SELECT COUNT(DISTINCT CONCAT(" +
                   "COALESCE(customer_id, -1), '|', COALESCE(item_id, -1), '|', " +
                   "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')" +
                   ")) FROM behavior_log " +
                   "WHERE event_type IN ('purchase', 'PURCHASE') " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until)",
           nativeQuery = true)
    long countPurchases(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("SELECT COUNT(b) FROM BehaviorLog b WHERE b.eventType IN ('product_view', 'PRODUCT_VIEW') " +
           "AND (:since IS NULL OR b.createdAt >= :since) " +
           "AND (:until IS NULL OR b.createdAt < :until)")
    long countProductViews(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    // ── 6. 인기 상품 TOP 5 ────────────────────────────────────────────────────
    @Query(value = "SELECT item_id, COUNT(*) AS cnt FROM behavior_log " +
                   "WHERE event_type = 'product_view' AND item_id IS NOT NULL " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until) " +
                   "GROUP BY item_id ORDER BY cnt DESC LIMIT 5",
           nativeQuery = true)
    List<Object[]> findTopProductsByView(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    // ── 7. 인기 페이지 TOP 5 ──────────────────────────────────────────────────
    @Query(value = "SELECT page_url, COUNT(*) AS cnt FROM behavior_log " +
                   "WHERE page_url IS NOT NULL AND page_url != '' " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until) " +
                   "GROUP BY page_url ORDER BY cnt DESC LIMIT 5",
           nativeQuery = true)
    List<Object[]> findTopPagesByView(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    // ── 8. 위시리스트 전환율 ──────────────────────────────────────────────────
    @Query(value = "SELECT COUNT(DISTINCT CONCAT(" +
                   "COALESCE(customer_id, -1), '|', COALESCE(item_id, -1), '|', DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')" +
                   ")) FROM behavior_log " +
                   "WHERE event_type IN ('wishlist_add', 'WISHLIST') " +
                   "AND customer_id IS NOT NULL " +
                   "AND item_id IS NOT NULL " +
                   "AND (:since IS NULL OR created_at >= :since) " +
                   "AND (:until IS NULL OR created_at < :until)",
           nativeQuery = true)
    long countWishlists(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query(value = "SELECT COUNT(DISTINCT CONCAT(" +
                   "COALESCE(w.customer_id, -1), '|', COALESCE(w.item_id, -1), '|', DATE_FORMAT(w.created_at, '%Y-%m-%d %H:%i')" +
                   ")) FROM behavior_log w " +
                   "WHERE w.event_type IN ('wishlist_add', 'WISHLIST') " +
                   "AND w.customer_id IS NOT NULL " +
                   "AND w.item_id IS NOT NULL " +
                   "AND (:since IS NULL OR w.created_at >= :since) " +
                   "AND (:until IS NULL OR w.created_at < :until) " +
                   "AND EXISTS ( " +
                   "  SELECT 1 FROM behavior_log p " +
                   "  WHERE p.event_type IN ('purchase', 'PURCHASE') " +
                   "  AND p.customer_id = w.customer_id " +
                   "  AND p.item_id = w.item_id " +
                   "  AND p.created_at >= w.created_at " +
                   "  AND (:since IS NULL OR p.created_at >= :since) " +
                   "  AND (:until IS NULL OR p.created_at < :until) " +
                   ")",
           nativeQuery = true)
    long countWishlistConversions(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);
}
