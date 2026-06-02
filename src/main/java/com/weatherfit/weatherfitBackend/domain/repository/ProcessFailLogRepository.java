package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weatherfit.weatherfitBackend.domain.entity.ProcessFailLog;

public interface ProcessFailLogRepository extends JpaRepository<ProcessFailLog, Long> {
}