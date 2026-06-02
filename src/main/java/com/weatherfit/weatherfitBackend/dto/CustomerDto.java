package com.weatherfit.weatherfitBackend.dto;

import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDto {

    private Long          id;
    private String        uid;
    private String        name;
    private String        email;
    private String        gender;
    private LocalDate     birthDate;
    private String        phone;
    private LocalDate     joinDate;
    private String        membershipLevel;
    private Integer       coldSensitivity;
    private String        activityLevel;
    private String        preferredStyle;
    private Boolean       marketingConsent;
    private Boolean       pushConsent;
    private Boolean       emailConsent;
    private Boolean       smsConsent;
    private Boolean       dmConsent;
    private Boolean       tmConsent;
    private LocalDate     lastLoginDate;
    private Boolean       isFraud;
    private String        joinChannel;
    private String        joinType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long          regionId;
    private String        nextMembershipLevel;
    private int           totalPurchaseAmount;
    private int           totalFeedbackCount;

    public static CustomerDto from(Customer c, String nextMembershipLevel,
                                   int totalPurchaseAmount, int totalFeedbackCount) {
        CustomerDto dto = new CustomerDto();
        dto.setId(c.getId());
        dto.setUid(c.getUid());
        dto.setRegionId(c.getRegion() != null ? c.getRegion().getId() : null);
        dto.setName(c.getName());
        dto.setEmail(c.getEmail());
        dto.setGender(c.getGender());
        dto.setBirthDate(c.getBirthDate());
        dto.setPhone(c.getPhone());
        dto.setJoinDate(c.getJoinDate());
        dto.setMembershipLevel(c.getMembershipLevel());
        dto.setColdSensitivity(c.getColdSensitivity());
        dto.setActivityLevel(c.getActivityLevel());
        dto.setPreferredStyle(c.getPreferredStyle());
        dto.setMarketingConsent(c.getMarketingConsent());
        dto.setPushConsent(c.getPushConsent());
        dto.setEmailConsent(c.getEmailConsent());
        dto.setSmsConsent(c.getSmsConsent());
        dto.setDmConsent(c.getDmConsent());
        dto.setTmConsent(c.getTmConsent());
        dto.setLastLoginDate(c.getLastLoginDate());
        dto.setIsFraud(c.getIsFraud());
        dto.setJoinChannel(c.getJoinChannel());
        dto.setJoinType(c.getJoinType());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        dto.setNextMembershipLevel(nextMembershipLevel);
        dto.setTotalPurchaseAmount(totalPurchaseAmount);
        dto.setTotalFeedbackCount(totalFeedbackCount);
        return dto;
    }
}
