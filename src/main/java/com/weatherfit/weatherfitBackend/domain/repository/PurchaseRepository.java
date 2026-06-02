package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import com.weatherfit.weatherfitBackend.domain.entity.Purchase;

import java.time.LocalDateTime;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findByCustomerId(Long customerId);
    List<Purchase> findByCustomerIdAndStatus(Long customerId, String status);
    List<Purchase> findByCustomerIdAndStatusAndPurchasedAtAfter(Long customerId, String status, LocalDateTime from);
    List<Purchase> findByCustomerIdAndStatusAndPurchasedAtBetween(
            Long customerId, String status, LocalDateTime start, LocalDateTime end);

    @Query("SELECT p FROM Purchase p WHERE p.customerId = :customerId" +
           " AND p.status IN :statuses AND p.purchasedAt >= :since")
    List<Purchase> findRecentByCustomerIdAndStatuses(
            @Param("customerId") Long customerId,
            @Param("statuses") List<String> statuses,
            @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(p.price), 0) FROM Purchase p" +
           " WHERE p.customerId = :customerId AND p.status = 'PURCHASED'")
    Integer sumPurchasedAmountByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT p.customerId, COALESCE(SUM(p.price), 0) FROM Purchase p" +
           " WHERE p.status = 'PURCHASED' GROUP BY p.customerId")
    List<Object[]> sumPurchasedAmountByCustomer();

    @Query("SELECT p.customerId, COALESCE(SUM(p.price), 0) FROM Purchase p" +
           " WHERE p.status = 'PURCHASED' AND p.purchasedAt BETWEEN :start AND :end" +
           " GROUP BY p.customerId")
    List<Object[]> sumPurchasedAmountByCustomerBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.price), 0) FROM Purchase p WHERE p.status = 'PURCHASED'")
    Long sumTotalRevenue();

    boolean existsByCustomerIdAndProductIdAndStatus(Long customerId, Long productId, String status);

    @Transactional
    @Modifying
    void deleteByCustomerIdAndProductIdAndStatus(Long customerId, Long productId, String status);
}
