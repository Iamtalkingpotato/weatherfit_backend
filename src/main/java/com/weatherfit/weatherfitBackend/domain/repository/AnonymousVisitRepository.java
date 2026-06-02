package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousVisit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousVisitRepository extends JpaRepository<AnonymousVisit, Long> {
}