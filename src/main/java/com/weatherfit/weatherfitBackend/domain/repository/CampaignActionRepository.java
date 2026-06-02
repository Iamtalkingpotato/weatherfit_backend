package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CampaignActionRepository extends JpaRepository<CampaignAction, Long> {

    List<CampaignAction> findByCampaignId(Long campaignId);

    @Transactional
    void deleteByCampaignId(Long campaignId);
}
