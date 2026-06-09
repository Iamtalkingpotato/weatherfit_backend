package com.weatherfit.weatherfitBackend.domain.repository;

import com.weatherfit.weatherfitBackend.domain.entity.CampaignAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CampaignActionRepository extends JpaRepository<CampaignAction, Long> {

    List<CampaignAction> findByCampaignId(Long campaignId);

    /** coupon_id로 역참조: 이 쿠폰을 사용하는 campaign_action 목록 */
    List<CampaignAction> findByCouponId(Long couponId);

    @Transactional
    void deleteByCampaignId(Long campaignId);
}
