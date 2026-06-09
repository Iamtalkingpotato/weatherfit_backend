package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.CustomerCoupon;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCouponRepository extends JpaRepository<CustomerCoupon, Long> {
    List<CustomerCoupon> findByCustomerId(Long customerId);

    boolean existsByCustomerIdAndCouponId(Long customerId, Long couponId);
}
