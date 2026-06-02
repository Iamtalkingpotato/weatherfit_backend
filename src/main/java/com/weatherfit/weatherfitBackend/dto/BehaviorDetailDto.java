package com.weatherfit.weatherfitBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorDetailDto {
    private Long    id;
    private String  customerName;
    private String  eventType;
    private String  pageUrl;
    private Integer duration;
    private Integer scrollDepth;
    private String  time;
}
