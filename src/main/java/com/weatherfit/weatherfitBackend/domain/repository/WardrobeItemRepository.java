package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.weatherfit.weatherfitBackend.domain.entity.WardrobeItem;
import java.time.LocalDateTime;
import java.util.List;

public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, Long> {
    List<WardrobeItem> findByCustomerId(Long customerId);

    @Query("SELECT w FROM WardrobeItem w WHERE w.customerId = :customerId AND w.createdAt >= :since")
    List<WardrobeItem> findRecentByCustomerId(
            @Param("customerId") Long customerId,
            @Param("since") LocalDateTime since);

    int countByCustomerId(Long customerId);
    int countByCustomerIdAndCategory(Long customerId, String category);
}