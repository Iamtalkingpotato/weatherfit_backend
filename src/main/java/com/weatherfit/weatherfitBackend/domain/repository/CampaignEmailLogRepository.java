package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.CampaignEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignEmailLogRepository extends JpaRepository<CampaignEmailLog, Long> {

    boolean existsByCampaignIdAndCustomerId(Long campaignId, Long customerId);
}
