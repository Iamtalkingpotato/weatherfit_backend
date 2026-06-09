package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.AnonymousPopupRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnonymousPopupRuleRepository extends JpaRepository<AnonymousPopupRule, Long> {
    List<AnonymousPopupRule> findByIsActiveTrue();
}
