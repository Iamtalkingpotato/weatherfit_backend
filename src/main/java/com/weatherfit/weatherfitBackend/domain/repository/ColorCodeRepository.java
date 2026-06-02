package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.ColorCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorCodeRepository extends JpaRepository<ColorCode, Long> {
}
