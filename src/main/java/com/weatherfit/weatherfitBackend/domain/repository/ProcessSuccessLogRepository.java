package com.weatherfit.weatherfitBackend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weatherfit.weatherfitBackend.domain.entity.ProcessSuccessLog;

public interface ProcessSuccessLogRepository extends JpaRepository<ProcessSuccessLog, Long> {
}