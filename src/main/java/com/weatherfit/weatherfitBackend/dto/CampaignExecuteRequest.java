package com.weatherfit.weatherfitBackend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CampaignExecuteRequest {
    private String emailSubject;
    private String emailBody;
}
