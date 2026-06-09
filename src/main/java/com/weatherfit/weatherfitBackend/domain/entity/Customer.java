package com.weatherfit.weatherfitBackend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 50)
    private String uid;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "membership_level", length = 10)
    @Builder.Default
    private String membershipLevel = "BASIC";

    @Column(name = "cold_sensitivity")
    @Builder.Default
    private Integer coldSensitivity = 3;

    @Column(name = "activity_level", length = 10)
    @Builder.Default
    private String activityLevel = "MEDIUM";

    @Column(name = "preferred_style", length = 20)
    private String preferredStyle;

    @Column(name = "marketing_consent")
    @Builder.Default
    private Boolean marketingConsent = false;

    @Column(name = "push_consent")
    @Builder.Default
    private Boolean pushConsent = false;

    @Column(name = "email_consent")
    @Builder.Default
    private Boolean emailConsent = false;

    @Column(name = "sms_consent")
    @Builder.Default
    private Boolean smsConsent = false;

    @Column(name = "dm_consent")
    @Builder.Default
    private Boolean dmConsent = false;

    @Column(name = "tm_consent")
    @Builder.Default
    private Boolean tmConsent = false;

    @Column(name = "last_login_date")
    private LocalDate lastLoginDate;

    @Column(name = "is_fraud")
    @Builder.Default
    private Boolean isFraud = false;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "join_channel", length = 20)
    private String joinChannel;

    @Column(name = "join_type", length = 20)
    private String joinType;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (joinDate == null) joinDate = LocalDate.now();
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
