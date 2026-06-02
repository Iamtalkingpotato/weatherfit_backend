package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_name", length = 100)
    private String couponName;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "type", length = 10)
    @Builder.Default
    private String type = "PERCENT";

    @Column(name = "discount_value")
    private Integer discountValue;

    @Column(name = "min_order_amount")
    @Builder.Default
    private Integer minOrderAmount = 0;

    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    @Column(name = "valid_days")
    @Builder.Default
    private Integer validDays = 30;

    @Column(name = "target_category", length = 50)
    private String targetCategory;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "issued_count")
    @Builder.Default
    private Integer issuedCount = 0;

    @Column(name = "used_count")
    @Builder.Default
    private Integer usedCount = 0;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "expired_at")
    private LocalDate expiredAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
