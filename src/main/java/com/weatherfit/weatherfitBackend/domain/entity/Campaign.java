package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaign")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category1", length = 50)
    private String category1;

    @Column(name = "category2", length = 50)
    private String category2;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "설계중";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "grace_days")
    @Builder.Default
    private Integer graceDays = 0;

    @Column(name = "customer_type", length = 20)
    @Builder.Default
    private String customerType = "개인";

    @Column(name = "visibility", length = 20)
    @Builder.Default
    private String visibility = "비공개";

    @Column(name = "tags", length = 200)
    private String tags;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "filter_subject", length = 100)
    private String filterSubject;

    @Column(name = "filter_conditions", columnDefinition = "TEXT")
    private String filterConditions;

    @Column(name = "anonymous_min_visits")
    private Integer anonymousMinVisits;

    @Column(name = "email_subject", length = 300)
    private String emailSubject;

    @Column(name = "email_body", columnDefinition = "TEXT")
    private String emailBody;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 응답 전용 — DB 컬럼 없음, 컨트롤러에서 채워서 반환 */
    @Transient
    @Builder.Default
    private List<CampaignAction> actions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
