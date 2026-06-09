package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.CampaignPopupLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface CampaignPopupLogRepository extends JpaRepository<CampaignPopupLog, Long> {

    boolean existsByCampaignIdAndCustomerIdAndShownDate(Long campaignId, Long customerId, LocalDate shownDate);
}
