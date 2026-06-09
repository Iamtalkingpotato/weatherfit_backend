package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_popup_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPopupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "shown_date")
    private LocalDate shownDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
