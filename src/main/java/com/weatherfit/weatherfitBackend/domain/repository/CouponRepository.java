package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
