package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_action")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** COUPON / POPUP / SMS */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "popup_message", columnDefinition = "TEXT")
    private String popupMessage;

    @Column(name = "sms_message", columnDefinition = "TEXT")
    private String smsMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
