package com.weatherfit.weatherfitBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorSummaryDto {
    private long   totalViews;
    private double avgDuration;
    private double avgScrollDepth;
}
