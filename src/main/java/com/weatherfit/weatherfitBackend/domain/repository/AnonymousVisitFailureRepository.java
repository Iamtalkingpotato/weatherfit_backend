package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousVisitFailure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousVisitFailureRepository extends JpaRepository<AnonymousVisitFailure, Long> {
}